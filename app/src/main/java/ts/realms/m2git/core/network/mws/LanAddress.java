package ts.realms.m2git.core.network.mws;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import timber.log.Timber;

/**
 * 局域网 IPv4 地址枚举（SimpleMiltonServer 证书 SAN 与 WebDavService 通知栏共用）。
 * <p>
 * 跳过规则（Android 手机场景）：
 * <ul>
 *   <li>回环接口 / 回环地址；</li>
 *   <li>未启用（down）接口；</li>
 *   <li>VPN（tun）、蜂窝（rmnet）、虚拟（dummy）接口；</li>
 *   <li>非 IPv4 地址。</li>
 * </ul>
 * 不保证顺序稳定（受系统接口枚举顺序影响）；返回空列表表示当前无可用 LAN 地址。
 * <p>
 * 双网卡（如 WiFi + 热点）场景下会返回多个地址，调用方按业务需要决定
 * 是显示全部（通知栏）还是截取列表（证书 SAN 不会截取，全部写入）。
 */
public final class LanAddress {

    private static final String[] EXCLUDED_INTERFACE_PREFIXES = {"tun", "rmnet", "dummy"};

    private LanAddress() {
    }

    /**
     * 返回所有非回环、非 VPN/蜂窝/虚拟接口上的 IPv4 地址。
     * 空列表 = 当前设备没有可用的 LAN 接口。
     */
    public static List<Inet4Address> listInet4() {
        List<Inet4Address> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface nif = en.nextElement();
                if (!isUsable(nif)) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        result.add((Inet4Address) a);
                    }
                }
            }
        } catch (Exception e) {
            Timber.tag("LanAddress").w(e, "enumerate LAN IPv4 failed");
        }
        return result;
    }

    /**
     * 与 {@link #listInet4()} 相同，但直接返回字符串形式
     * （用于证书 SAN、通知栏 URL）。
     */
    public static List<String> listInet4Strings() {
        List<Inet4Address> addrs = listInet4();
        List<String> result = new ArrayList<>(addrs.size());
        for (Inet4Address a : addrs) {
            result.add(a.getHostAddress());
        }
        return result;
    }

    private static boolean isUsable(NetworkInterface nif) {
        try {
            if (!nif.isUp() || nif.isLoopback()) return false;
            String name = nif.getName();
            for (String prefix : EXCLUDED_INTERFACE_PREFIXES) {
                if (name.startsWith(prefix)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}