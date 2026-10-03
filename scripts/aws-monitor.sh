#!/usr/bin/env bash
set -uo pipefail

readonly APP_ROOT="/opt/patitoland"
readonly PROJECT_ROOT="${APP_ROOT}/Patitoland"
readonly ENV_FILE="${PROJECT_ROOT}/.env"
readonly STATE_DIR="${APP_ROOT}/monitor-state"
readonly STATE_FILE="${STATE_DIR}/state"
readonly REMINDER_SECONDS=21600
readonly DISK_WARNING_PERCENT=80
readonly BACKUP_MAX_AGE_SECONDS=129600

mode="${1:-run}"
now="$(date +%s)"
host="$(hostname)"
failures=()

record_failure() {
  failures+=("$1")
}

read_env_value() {
  local key="$1"
  local line value

  line="$(grep -m1 "^${key}=" "${ENV_FILE}" 2>/dev/null)" || return 1
  value="${line#*=}"
  if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "${value}"
}

send_mail() {
  local subject="$1"
  local body="$2"
  local api_key mail_to mail_from payload response_file http_code

  api_key="$(read_env_value RESEND_API_KEY)" || return 1
  mail_to="$(read_env_value MAIL_TO)" || return 1
  mail_from="$(read_env_value MAIL_FROM)" || return 1

  payload="$(jq -n \
    --arg from "${mail_from}" \
    --arg to "${mail_to}" \
    --arg subject "${subject}" \
    --arg text "${body}" \
    '{from: $from, to: [$to], subject: $subject, text: $text}')"
  response_file="$(mktemp)"

  http_code="$(curl --silent --show-error --max-time 20 \
    --output "${response_file}" --write-out '%{http_code}' \
    --request POST 'https://api.resend.com/emails' \
    --header "Authorization: Bearer ${api_key}" \
    --header 'Content-Type: application/json' \
    --data "${payload}")" || {
      rm -f "${response_file}"
      return 1
    }

  rm -f "${response_file}"
  [[ "${http_code}" =~ ^2[0-9][0-9]$ ]]
}

check_http() {
  local label="$1"
  local url="$2"
  local code

  code="$(curl --silent --show-error --location --max-time 20 \
    --output /dev/null --write-out '%{http_code}' "${url}" 2>/dev/null)" || code="000"
  [[ "${code}" == "200" ]] || record_failure "${label}: HTTP ${code}"
}

check_container() {
  local container="$1"
  local state running health

  state="$(docker inspect --format '{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
    "${container}" 2>/dev/null)" || {
      record_failure "Contenedor ${container}: no existe"
      return
    }

  IFS='|' read -r running health <<<"${state}"
  [[ "${running}" == "true" ]] || record_failure "Contenedor ${container}: detenido"
  if [[ "${health}" != "none" && "${health}" != "healthy" ]]; then
    record_failure "Contenedor ${container}: estado ${health}"
  fi
}

check_disk() {
  local usage
  usage="$(df -P / | awk 'NR == 2 {gsub(/%/, "", $5); print $5}')"
  if [[ -z "${usage}" || "${usage}" -ge "${DISK_WARNING_PERCENT}" ]]; then
    record_failure "Disco raíz: ${usage:-desconocido}% usado"
  fi
}

check_backup() {
  local latest backup_file modified age
  latest="$(readlink -f "${APP_ROOT}/backups/daily/latest" 2>/dev/null)" || latest=""

  if [[ -z "${latest}" || ! -d "${latest}" ]]; then
    record_failure "Copia diaria: no se encuentra el directorio latest"
    return
  fi

  for backup_file in "${latest}/patitoland.dump" "${latest}/inventory.db"; do
    if [[ ! -s "${backup_file}" ]]; then
      record_failure "Copia diaria: falta $(basename "${backup_file}")"
      continue
    fi
    modified="$(stat -c %Y "${backup_file}")"
    age="$((now - modified))"
    if [[ "${age}" -gt "${BACKUP_MAX_AGE_SECONDS}" ]]; then
      record_failure "Copia diaria: $(basename "${backup_file}") supera 36 horas"
    fi
  done
}

write_state() {
  local status="$1"
  local last_sent="$2"
  local message_hash="$3"
  local temporary

  temporary="${STATE_FILE}.tmp"
  printf 'status=%s\nlast_sent=%s\nmessage_hash=%s\n' \
    "${status}" "${last_sent}" "${message_hash}" > "${temporary}"
  chmod 600 "${temporary}"
  mv "${temporary}" "${STATE_FILE}"
}

state_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "${STATE_FILE}" 2>/dev/null | head -1
}

mkdir -p "${STATE_DIR}"
chmod 700 "${STATE_DIR}"
exec 9>"${STATE_DIR}/monitor.lock"
flock -n 9 || exit 0

check_http "Web principal" "https://patitoland-terrassa.es/"
check_http "API de disponibilidad" \
  "https://patitoland-terrassa.es/api/bookings/availability?month=$(date +%Y-%m)"
check_http "Inventario" "https://patitoland-terrassa.es/inventario/"

for container in patitoland-postgres patitoland-backend patitoland-caddy stockfacil; do
  check_container "${container}"
done

check_disk
check_backup

timestamp="$(date --iso-8601=seconds)"
if [[ "${#failures[@]}" -eq 0 ]]; then
  details="Todos los controles están correctos."
else
  details="$(printf -- '- %s\n' "${failures[@]}")"
fi

if [[ "${mode}" == "test" ]]; then
  send_mail \
    "[PatitoLand] Monitor de producción activado" \
    "El monitor se ha instalado correctamente en ${host}.\n\n${details}\nFecha: ${timestamp}" || {
      printf 'No se pudo enviar el correo de prueba.\n' >&2
      exit 1
    }
  printf 'Test monitoring email sent.\n'
  exit 0
fi

previous_status="$(state_value status)"
previous_sent="$(state_value last_sent)"
previous_hash="$(state_value message_hash)"
previous_sent="${previous_sent:-0}"

if [[ "${#failures[@]}" -gt 0 ]]; then
  message_hash="$(printf '%s' "${details}" | sha256sum | awk '{print $1}')"
  should_send=false
  if [[ "${previous_status}" != "failed" || "${previous_hash}" != "${message_hash}" ]]; then
    should_send=true
  elif (( now - previous_sent >= REMINDER_SECONDS )); then
    should_send=true
  fi

  if [[ "${should_send}" == "true" ]]; then
    send_mail \
      "[PatitoLand] ALERTA de producción" \
      "Se han detectado problemas en ${host}:\n\n${details}\nFecha: ${timestamp}" || {
        printf 'Monitoring alert email failed.\n' >&2
        exit 1
      }
    previous_sent="${now}"
  fi
  write_state "failed" "${previous_sent}" "${message_hash}"
  printf 'Monitoring failures detected:\n%s' "${details}"
  exit 1
fi

if [[ "${previous_status}" == "failed" ]]; then
  send_mail \
    "[PatitoLand] Servicio recuperado" \
    "Todos los controles vuelven a estar correctos en ${host}.\nFecha: ${timestamp}" || {
      printf 'Monitoring recovery email failed.\n' >&2
      exit 1
    }
fi

write_state "ok" "0" ""
printf 'All monitoring checks passed.\n'
