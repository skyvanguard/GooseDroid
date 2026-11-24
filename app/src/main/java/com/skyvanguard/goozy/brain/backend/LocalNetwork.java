package com.skyvanguard.goozy.brain.backend;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Reconoce direcciones de la red local, las únicas a las que se permite
 * hablar sin cifrado.
 */
public final class LocalNetwork {
    private static final int IPV4_PARTS = 4;
    private static final int MAX_OCTET = 255;

    private LocalNetwork() {
    }

    public static boolean isLocalUrl(String url) {
        if (url == null) return false;
        try {
            String host = new URI(url.trim()).getHost();
            return host != null && isLocalHost(host);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Solo direcciones literales y nombres reservados: un nombre de dominio
     * cualquiera podría resolver a una dirección pública.
     */
    static boolean isLocalHost(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        if (name.equals("localhost") || name.endsWith(".local") || name.endsWith(".lan")) {
            return true;
        }
        int[] octets = parseIpv4(name);
        if (octets == null) return false;

        int first = octets[0];
        int second = octets[1];
        return first == 10
                || first == 127
                || (first == 192 && second == 168)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 169 && second == 254)
                // CGNAT: lo usan las VPN tipo Tailscale
                || (first == 100 && second >= 64 && second <= 127);
    }

    private static int[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != IPV4_PARTS) return null;
        int[] octets = new int[IPV4_PARTS];
        for (int i = 0; i < IPV4_PARTS; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return null;
            for (int j = 0; j < part.length(); j++) {
                if (!Character.isDigit(part.charAt(j))) return null;
            }
            octets[i] = Integer.parseInt(part);
            if (octets[i] > MAX_OCTET) return null;
        }
        return octets;
    }
}
