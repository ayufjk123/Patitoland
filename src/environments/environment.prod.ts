export const environment = {
  production: true,
  // Same-origin: Caddy reverse-proxies /api to the Spring Boot backend.
  // Cloudflare Tunnel terminates TLS at https://patitoland-terrassa.es and
  // forwards to the local Caddy container.
  apiUrl: '/api'
};
