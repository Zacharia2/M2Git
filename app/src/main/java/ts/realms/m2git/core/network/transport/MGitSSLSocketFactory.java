package ts.realms.m2git.core.network.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


public class MGitSSLSocketFactory extends SSLSocketFactory {

    private static final String[] enabledProtocols = new String[]{"TLSv1.3", "TLSv1.2"};
    private final SSLSocketFactory wrappedSSLSocketFactory;

    public MGitSSLSocketFactory(SSLSocketFactory wrapped) {
        wrappedSSLSocketFactory = wrapped;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return wrappedSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return wrappedSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return modifySocket(wrappedSSLSocketFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return modifySocket(wrappedSSLSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return modifySocket(wrappedSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return modifySocket(wrappedSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return modifySocket(wrappedSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return modifySocket(wrappedSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }


    private Socket modifySocket(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            sslSocket.setEnabledProtocols(enabledProtocols);
        }
        return socket;
    }
}
