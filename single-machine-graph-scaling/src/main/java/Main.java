import ai.grakn.Grakn;

import java.io.IOException;

/**
 *
 */
public class Main {


    public static void main(String[] args) throws IOException {
        // initialise the connection to engine
        String engineHostname = Grakn.DEFAULT_URI;
        if (args.length>0) {engineHostname = args[0];}

        Test test = new Test(engineHostname);

        test.setUp();

        test.countIT();

    }

}
