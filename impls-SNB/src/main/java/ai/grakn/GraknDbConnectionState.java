package ai.grakn;

import com.ldbc.driver.DbConnectionState;

import java.io.IOException;
import java.util.Map;

/**
 * @author Felix Chapman
 */
public class GraknDbConnectionState extends DbConnectionState {

    private final GraknSession session;

    public GraknDbConnectionState(Map<String, String> properties) {

        String uri;

        uri = properties.getOrDefault("uri", "localhost:4567");


        String keyspace;

        keyspace = properties.getOrDefault("keyspace", "snb");


        GraknSession session = Grakn.session(uri, keyspace);
        this.session = session;
    }

    @Override
    public void close() throws IOException {
        session.close();
    }

    GraknSession session() {
        return this.session;
    }
}
