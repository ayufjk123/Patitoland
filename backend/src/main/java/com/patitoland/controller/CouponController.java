package com.patitoland.controller;

import com.patitoland.model.Coupon;
import com.patitoland.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Staff-only coupon validation & management.
 * Every endpoint requires the X-Coupon-Token header to match coupon.admin-token.
 * The /cupones front-end page collects that token as a password and sends it on each call.
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    // Unambiguous alphabet (no 0/O/1/I) for human-readable codes.
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_GENERATE = 500;

    private final CouponRepository couponRepository;

    @Value("${coupon.admin-token:}")
    private String adminToken;

    public CouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    private boolean authorized(String token) {
        return adminToken != null && !adminToken.isBlank() && adminToken.equals(token);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }

    /** Lightweight endpoint the front-end uses to check the password. */
    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> checkAuth(@RequestHeader(value = "X-Coupon-Token", required = false) String token) {
        if (!authorized(token)) return unauthorized();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Validates a code WITHOUT redeeming it. */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestHeader(value = "X-Coupon-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        if (!authorized(token)) return unauthorized();
        String code = normalize(body.get("code"));
        if (code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code required"));
        }
        Optional<Coupon> found = couponRepository.findByCodeIgnoreCase(code);
        if (found.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "INVALID", "code", code));
        }
        Coupon c = found.get();
        return ResponseEntity.ok(describe(c, statusOf(c)));
    }

    /** Validates and, if valid + single-use, marks the coupon as redeemed. */
    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
            @RequestHeader(value = "X-Coupon-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        if (!authorized(token)) return unauthorized();
        String code = normalize(body.get("code"));
        if (code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code required"));
        }
        Optional<Coupon> found = couponRepository.findByCodeIgnoreCase(code);
        if (found.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "INVALID", "code", code));
        }
        Coupon c = found.get();
        String status = statusOf(c);
        if (!"VALID".equals(status)) {
            return ResponseEntity.ok(describe(c, status));
        }
        if (c.isSingleUse()) {
            c.setRedeemed(true);
            c.setRedeemedAt(LocalDateTime.now());
            couponRepository.save(c);
        }
        return ResponseEntity.ok(describe(c, "REDEEMED"));
    }

    /** Generates one or more coupons. */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestHeader(value = "X-Coupon-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        if (!authorized(token)) return unauthorized();

        Integer count = integerValue(body.get("count"));
        if (count == null) count = 1;
        if (count < 1 || count > MAX_GENERATE) {
            return ResponseEntity.badRequest().body(Map.of("error", "count must be 1.." + MAX_GENERATE));
        }
        String discountType = body.get("discountType") != null
                ? body.get("discountType").toString().trim().toUpperCase(Locale.ROOT)
                : "PERCENT";
        if (!"PERCENT".equals(discountType) && !"FIXED".equals(discountType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "discountType must be PERCENT or FIXED"));
        }
        Double discountValue = doubleValue(body.get("discountValue"));
        if (discountValue == null || !Double.isFinite(discountValue) || discountValue <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "discountValue must be greater than 0"));
        }
        if ("PERCENT".equals(discountType) && discountValue > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "percentage discount cannot exceed 100"));
        }
        boolean singleUse = body.get("singleUse") == null || Boolean.parseBoolean(body.get("singleUse").toString());
        String label = nullableText(body.get("label"));
        if (label != null && label.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "label cannot exceed 100 characters"));
        }
        LocalDate validUntil = null;
        if (body.get("validUntil") != null && !body.get("validUntil").toString().isBlank()) {
            try {
                validUntil = LocalDate.parse(body.get("validUntil").toString());
            } catch (DateTimeParseException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "validUntil must use YYYY-MM-DD"));
            }
            if (validUntil.isBefore(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of("error", "validUntil cannot be in the past"));
            }
        }

        List<String> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Coupon c = new Coupon();
            c.setCode(uniqueCode());
            c.setDiscountType(discountType);
            c.setDiscountValue(discountValue);
            c.setValidUntil(validUntil);
            c.setSingleUse(singleUse);
            c.setRedeemed(false);
            c.setCreatedAt(LocalDateTime.now());
            c.setLabel(label);
            couponRepository.save(c);
            created.add(c.getCode());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "count", created.size(),
                "codes", created
        ));
    }

    /** Lists all coupons (most recent first). */
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Coupon-Token", required = false) String token) {
        if (!authorized(token)) return unauthorized();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Coupon c : couponRepository.findAllByOrderByCreatedAtDesc()) {
            out.add(describe(c, statusOf(c)));
        }
        return ResponseEntity.ok(out);
    }

    // --- helpers ---

    private String statusOf(Coupon c) {
        if (c.getValidUntil() != null && LocalDate.now().isAfter(c.getValidUntil())) {
            return "EXPIRED";
        }
        if (c.isSingleUse() && c.isRedeemed()) {
            return "USED";
        }
        return "VALID";
    }

    private Map<String, Object> describe(Coupon c, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("code", c.getCode());
        m.put("discountType", c.getDiscountType());
        m.put("discountValue", c.getDiscountValue());
        m.put("validUntil", c.getValidUntil() != null ? c.getValidUntil().toString() : null);
        m.put("singleUse", c.isSingleUse());
        m.put("redeemed", c.isRedeemed());
        m.put("redeemedAt", c.getRedeemedAt() != null ? c.getRedeemedAt().toString() : null);
        m.put("label", c.getLabel());
        return m;
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private Integer integerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double doubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String nullableText(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String uniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
            code = sb.toString();
        } while (couponRepository.existsByCodeIgnoreCase(code));
        return code;
    }
}
