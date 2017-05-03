package act.security;

import act.TestBase;
import act.app.util.AppCrypto;
import act.conf.AppConfig;
import org.junit.Test;
import org.osgl.http.H;
import org.osgl.util.S;

public abstract class SecureTicketCodecTest extends TestBase {

    protected AppCrypto crypto() {
        return new AppCrypto(new AppConfig());
    }

    protected abstract SecureTicketCodec codec();

    @Test
    public void test() {
        H.Session session = new H.Session();
        String foo = S.random();
        session.put("foo", foo);
        prepareSession(session);
        SecureTicketCodec codec = codec();
        String ticket = codec.createTicket(session);
        H.Session session2 = codec.parseTicket(ticket);
        verifySession(session, session2);
    }

    protected void prepareSession(H.Session session) {
    }

    public void verifySession(H.Session original, H.Session decoded) {
        eq(original, decoded);
    }
}
