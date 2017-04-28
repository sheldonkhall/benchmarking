package ai.grakn.benchmark.rungraqlqueries;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import ai.grakn.client.QueryClient;
import mjson.Json;

/**
 * <p>
 * A simple Java program a list of Graql queries against an GRAKN Engine cluster, using
 * a number of users.
 * </p>
 * 
 * <p>
 * The program can be called for example like this: 
 * <br><br>
 * <code>BenchRunner -k  snb -s localhost -p 4567 -c 5 ~/bench/generated-queries.json</code>
 * <br><br>
 * To run against the 'snb' keyspace on localhost:4567 with 5 user and go through all queries in 
 * the generated-queries.json file. 
 * </p>
 * <p>
 * You can run the above via the Maven exec plugin so you don't have to worry about constructing
 * a class path: mvn exec:java -Dexec.args="-k  snb -s localhost -p 4567 -c 5 generated-queries.json"
 * </p>
 * 
 * @author borislav
 *
 */
public class BenchRunner {
    public static final String DEFAULT_KEYSPACE = "grakn";
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "4567";
    private static final String DEFAULT_USERS = "10";
    
    static final String totalTime = "totalTime";
    static final String operationCount = "operationCount";
    static final String successCount = "successCount";
    static final String failedCount = "failedCount";
    
    static ConcurrentHashMap<Thread, Json> userInfoMap = new ConcurrentHashMap<Thread, Json>();
    
    static ThreadLocal<Json> userInfo = new ThreadLocal<Json>() {
        @Override
        protected Json initialValue() {
            Json info = Json.object(totalTime, 0l, 
                                    successCount, 0,
                                    failedCount, 0);
            userInfoMap.put(Thread.currentThread(), info);
            return info;
        }        
    };
    
    static Map<String, String> parseCommandLine(String optionsSpec, String positionalSpec, String [] argv) {
        Collection<String> optionNames = Arrays.asList(optionsSpec.split(","));
        String [] positionalNames = positionalSpec.split(",");
        HashMap<String, String> opts = new HashMap<String, String>();
        int pos = 0;
        while (pos < argv.length) {            
            if (!argv[pos].startsWith("-"))
                break;
            if (pos >= argv.length - 1)
                die("Missing option value for option " + argv[pos]);
            String name = argv[pos].substring(1); 
            if (optionNames.contains(name))
            opts.put(name, argv[pos + 1]);
            pos += 2;
        }        
        for (int i = 0; pos < argv.length && i < positionalNames.length; i++) {
            opts.put(positionalNames[i], argv[pos++]);
        }
        return opts;
    }
    
    private static void syntax() {
        System.out.println("Syntax: BenchRunner -k keyspace -s server -p port query_filename.");
    }
    
    private static void die(String msg) {
        System.out.println(msg);
        System.exit(-1);
    }
            
    
    private static <T> List<Supplier<T>> parseQuerySet(final Function<String, T> queryExecutor, final Path file) {
        try {
            final Json spec = Json.read(file.toUri().toURL());
            final ArrayList<Supplier<T>> actions = new ArrayList<Supplier<T>>();
            spec.at("queries").asJsonList().forEach(q -> {
                actions.add(() -> {
                    return queryExecutor.apply(q.at("graql").asString());
                });
            });
            return actions;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    static void report(Collection<Json> infoCollection) {
        infoCollection.forEach(System.out::println);
        double total_time = infoCollection.stream().map(j -> j.at(totalTime)).reduce(Json.make(0), 
              (j1, j2) -> Json.make(j1.asLong() + j2.asLong())).asDouble() / 1000.0;
        double total_success = infoCollection.stream().map(j -> j.at(successCount)).reduce(Json.make(0), 
                (j1, j2) -> Json.make(j1.asInteger() + j2.asInteger())).asDouble();
        System.out.println("Average time per successful query: " + total_success/total_time);
    }
    
    public static void main(String[] args) {
        Map<String, String> opts = parseCommandLine("k,s,p,c", "file", args);        
        String keyspace = opts.getOrDefault("k", DEFAULT_KEYSPACE).toString();
        String host = opts.getOrDefault("s", DEFAULT_HOST).toString();
        int port = Integer.parseInt(opts.getOrDefault("p",  DEFAULT_PORT).toString());
        int numberOfUsers = Integer.parseInt(opts.getOrDefault("c",  DEFAULT_USERS).toString());
        String filename = opts.getOrDefault("file", null);
        if (filename == null) {
            syntax();
            die("Missing file with queries.\n\n");            
        }
        
        final QueryClient client = new QueryClient().host(host).port(port).keyspace(keyspace);
        
        Function<String, Json> queryExecutor = graqlString -> {
            long start = System.currentTimeMillis();
            Json info = userInfo.get();
            try {
                Json result = client.query(graqlString);
                long time = System.currentTimeMillis() - start;
                info.set(totalTime, info.at(totalTime).asLong() + time);
                info.set(successCount, info.at(successCount).asInteger() + 1);
                return result;
            }
            catch (Exception ex) {
                ex.printStackTrace(System.err);
                info.set(failedCount, info.at(failedCount).asInteger() + 1); 
                return Json.nil();
            }
        };
        
        try {
            List<Supplier<Json>> queries = parseQuerySet(queryExecutor, Paths.get(filename));
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfUsers);
            queries.forEach(q -> executorService.submit(() -> {
                q.get(); // ignoring the query response here....
            }));
            executorService.shutdown();
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            report(userInfoMap.values());
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(-1);            
        }
        
// The following code contains an example running in embedded mode. Which involve all GRAKN dependencies    
// rather than just the REST client. If such an option is added, better do it via some clever class loading
// so that 
        
//        List<Function<GraknGraph, ?>> queries = new ArrayList<Function<GraknGraph, ?>>();
//        try (Stream<String> stream = Files.lines(Paths.get(filename))) {
//            stream.forEach(line -> {
//                queries.add(graph -> {
//                   String firstname = line.split("\\|")[1];
//                   System.out.println("Firstname=" + firstname);
//                   return graph.graql().match(Graql.var("x").isa("person").has("first-name", 
//                               firstname)).limit(1).execute();
//                });
//            });
//        } catch (IOException e) {
//            e.printStackTrace(System.err);
//            System.exit(-1);
//        }
        
//        try (GraknSession session = Grakn.session(uri, keyspace)) {
//            ExecutorService executorService = Executors.newFixedThreadPool(numberOfUsers);
//            queries.forEach(q -> executorService.submit(() -> {
//                try (GraknGraph graph = session.open(GraknTxType.READ)) {
//                    System.out.println(q.apply(graph));
//                }
//            }));
//            executorService.shutdown();
//            try {
//                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
//            } catch (InterruptedException e) {
//                e.printStackTrace(System.err);
//            }
//        }
    }
}
