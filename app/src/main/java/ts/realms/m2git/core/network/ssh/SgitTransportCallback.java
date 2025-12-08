package ts.realms.m2git.core.network.ssh;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

/**
 * Created by sheimi on 8/22/13.
 */
public class SgitTransportCallback implements TransportConfigCallback {

    private final SGitSessionFactory ssh;

    public SgitTransportCallback() {
        ssh = new SGitSessionFactory();
    }

    @Override
    public void configure(Transport tn) {
        if (tn instanceof SshTransport) {
            ((SshTransport) tn).setSshSessionFactory(ssh);
        }
    }
}
