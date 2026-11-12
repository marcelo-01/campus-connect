package br.com.campusconnect.publicacao.service.previa;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * Resolve nomes como o sistema, mas recusa endereços internos: loopback, redes privadas, link-local
 * (como o {@code 169.254.169.254} dos metadados de nuvem), CGNAT, IPv6 de uso local e os IPv4 embutidos
 * em IPv6. Se qualquer endereço do nome for interno, o nome inteiro é recusado.
 *
 * <p>É a resolução que o cliente HTTP usa para conectar, então vale também para cada redirecionamento e
 * não deixa brecha de <em>DNS rebinding</em>: o endereço conferido é o mesmo da conexão.
 */
class FiltroEnderecos implements DnsResolver {

    private final boolean permitirRedeLocal;

    FiltroEnderecos(boolean permitirRedeLocal) {
        this.permitirRedeLocal = permitirRedeLocal;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] enderecos = SystemDefaultDnsResolver.INSTANCE.resolve(host);
        if (!permitirRedeLocal && Arrays.stream(enderecos).anyMatch(FiltroEnderecos::interno)) {
            throw new UnknownHostException("Endereço interno recusado: " + host);
        }
        return enderecos;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
    }

    static boolean interno(InetAddress endereco) {
        if (endereco.isAnyLocalAddress() || endereco.isLoopbackAddress() || endereco.isLinkLocalAddress()
                || endereco.isSiteLocalAddress() || endereco.isMulticastAddress()) {
            return true;
        }
        byte[] b = endereco.getAddress();
        if (endereco instanceof Inet4Address) {
            int a0 = b[0] & 0xFF;
            int a1 = b[1] & 0xFF;
            return a0 == 0                                  // 0.0.0.0/8
                    || (a0 == 100 && (a1 & 0xC0) == 64)     // 100.64.0.0/10 (CGNAT)
                    || (a0 == 192 && a1 == 0 && (b[2] & 0xFF) == 0) // 192.0.0.0/24
                    || (a0 == 198 && (a1 == 18 || a1 == 19)) // 198.18.0.0/15
                    || a0 >= 240;                           // reservados e broadcast
        }
        if (endereco instanceof Inet6Address) {
            if ((b[0] & 0xFE) == 0xFC) {                    // fc00::/7 (uso local)
                return true;
            }
            if (zeros(b, 0, 10) && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                return interno(ipv4(b, 12));                // ::ffff:a.b.c.d
            }
            if (zeros(b, 0, 12)) {
                return true;                                // ::a.b.c.d (obsoleto) e ::
            }
            if ((b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF
                    && (b[3] & 0xFF) == 0x9B && zeros(b, 4, 12)) {
                return interno(ipv4(b, 12));                // 64:ff9b::/96 (NAT64)
            }
            if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
                return interno(ipv4(b, 2));                 // 2002::/16 (6to4)
            }
        }
        return false;
    }

    private static boolean zeros(byte[] bytes, int de, int ate) {
        for (int i = de; i < ate; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress ipv4(byte[] bytes, int de) {
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(bytes, de, de + 4));
        } catch (UnknownHostException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
