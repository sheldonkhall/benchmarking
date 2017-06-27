import ai.grakn.Grakn;
import ai.grakn.GraknGraph;
import ai.grakn.GraknSession;
import ai.grakn.GraknTxType;
import ai.grakn.client.BatchMutatorClient;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.RoleType;
import ai.grakn.graph.internal.computer.GraknSparkComputer;
import ai.grakn.graql.QueryBuilderImplMock;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.internal.query.ComputeQueryBuilderImplMock;
import ai.grakn.graql.internal.query.analytics.CountQueryImplMock;
import ai.grakn.graql.internal.query.analytics.MaxQueryImplMock;
import ai.grakn.graql.internal.query.analytics.MeanQueryImplMock;
import ai.grakn.graql.internal.query.analytics.MedianQueryImplMock;
import ai.grakn.graql.internal.query.analytics.MinQueryImplMock;
import ai.grakn.graql.internal.query.analytics.StdQueryImplMock;
import ai.grakn.graql.internal.query.analytics.SumQueryImplMock;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ai.grakn.graql.Graql.insert;
import static ai.grakn.graql.Graql.var;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

/**
 *
 */
public class Test {

    // connection parameters
    final String engineHostname;

    // test parameters
    final int NUM_SUPER_NODES = 10; // the number of supernodes to generate in the test graph
    final int MAX_SIZE = 10000; // the maximum number of non super nodes to add to the test graph
    final int NUM_DIVS = 4; // the number of divisions of the MAX_SIZE to use in the scaling test
    final int REPEAT = 3; // the number of times to repeat at each size for average runtimes
    final int MAX_WORKERS = Runtime.getRuntime().availableProcessors(); // the maximum number of workers that spark should use
    final int WORKER_DIVS = 4; // the number of divisions of MAX_WORKERS to use for testing

    // test variables
    int STEP_SIZE;
    List<Integer> graphSizes;
    List<Integer> workerNumbers;
    List<String> headers;

    public Test(String engineHostname) {
        this.engineHostname = engineHostname;
    }

    public void setUp() {
        // compute the sample of graph sizes
        STEP_SIZE = MAX_SIZE/NUM_DIVS;
        graphSizes = new ArrayList<>();
        for (int i = 1;i < NUM_DIVS;i++) graphSizes.add(i*STEP_SIZE);
        graphSizes.add(MAX_SIZE);
        STEP_SIZE = MAX_WORKERS/WORKER_DIVS;
        workerNumbers = new ArrayList<>();
        for (int i = 1;i <= WORKER_DIVS;i++) workerNumbers.add(i*STEP_SIZE);

        headers = new ArrayList<>();
        headers.add("Size");
        headers.addAll(workerNumbers.stream().map(String::valueOf).collect(Collectors.toList()));
    }

    public static String randomKeyspace(){
        return "a"+ UUID.randomUUID().toString().replaceAll("-", "");
    }

    public void countIT() throws IOException {
        CSVPrinter printer = createCSVPrinter("countIT.txt");
        String keyspace = randomKeyspace();
        Set<String> superNodes;
        Long emptyCount;

        try (GraknSession session = Grakn.session(engineHostname, keyspace)) {

            // Insert super nodes into graph
            simpleOntology(session);

            // get a count before adding any data
            try (GraknGraph graph = session.open(GraknTxType.READ)) {
                emptyCount = graph.admin().getTinkerTraversal().count().next();
                System.out.println("gremlin count before data is: " + emptyCount);
            }

            superNodes = makeSuperNodes(session);
        }

        int previousGraphSize = 0;
        for (int graphSize : graphSizes) {
            System.out.println("current scale - super " + NUM_SUPER_NODES + " - nodes " + graphSize);
            Long conceptCount = (long) (NUM_SUPER_NODES * (graphSize + 1) + graphSize);
            printer.print(String.valueOf(conceptCount));

            System.out.println("start generate graph " + System.currentTimeMillis() / 1000L + "s");
            addNodesToSuperNodes(keyspace, superNodes, previousGraphSize, graphSize);
            previousGraphSize = graphSize;
            System.out.println("stop generate graph " + System.currentTimeMillis() / 1000L + "s");

            Long gremlinCount = (long) (NUM_SUPER_NODES * (3 * graphSize + 1) + graphSize);
            try (GraknSession session = Grakn.session(engineHostname, keyspace)) {
                try (GraknGraph graph = session.open(GraknTxType.READ)) {
                    System.out.println("gremlin count is: " +
                            graph.admin().getTinkerTraversal().count().next());
                }
            }
            gremlinCount += emptyCount;
            System.out.println("expected gremlin count is: " + gremlinCount);

            try (GraknSession session = Grakn.session(engineHostname, keyspace)) {
                for (int workerNumber : workerNumbers) {
                    System.out.println("Setting number of workers to: " + workerNumber);

                    Long countTime = 0L;
                    // close the spark context when changing spark configs
                    GraknSparkComputer.clear();

                    for (int i = 0; i < REPEAT; i++) {
                        System.out.println("repeat number: " + i);
                        Long startTime = System.currentTimeMillis();
                        try (GraknGraph graph = session.open(GraknTxType.READ)) {
                            Long count = getCountQuery(graph, workerNumber).execute();
                            if (!conceptCount.equals(count)) {
                                throw new RuntimeException(
                                        "The concept count should be: "
                                                + String.valueOf(conceptCount)
                                                + " but is " + String.valueOf(count) + " instead.");
                            }
                            System.out.println("count: " + count);
                        }
                        Long stopTime = System.currentTimeMillis();
                        countTime += stopTime - startTime;
                        System.out.println("count time: " + (stopTime - startTime) / 1000);
                    }

                    countTime /= REPEAT * 1000;
                    System.out.println("time to count: " + countTime);
                    printer.print(String.valueOf(countTime));
                }
            }
            printer.println();
            printer.flush();
        }

        printer.flush();
        printer.close();
    }

    /**
     * This test creates a graph of S*N*2 entities in total where: S is the total number of steps and N is the number of
     * entities per step. Each entity has a single numeric resource attached with the values chosen so that each size
     * of graph results in different values for the min, max, mean, sum, std. The median value always remains the same
     * but because the graph is growing, this in itself is a good test of median. The values follow the pattern:
     *
     * STEP g=1:         5 6 14 16
     * STEP g=2:     3 4 5 6 14 16 18 20
     * STEP g=3: 1 2 3 4 5 6 14 16 18 20 22 24
     *
     * for S = 3, N = 2.
     *
     * To generate the sequence there are two recursive formulae, one for the lower half of values v_m, another for the
     * upper half V_m:
     *
     * v_m+1 = v_m - 1, v_1 = S*N
     * V_m+1 = V_m + 2, V_1 = (S*N + 1)*2
     *
     * The sum of these values at STEP g is given by the formula:
     *
     * sum(g) = g*N(1/2 + g*N/2 + 3*S*N + 1)
     *
     * The min:
     *
     * min(g) = (S-g)*N + 1
     *
     * The max:
     *
     * max(g) = 2*N*(g+S)
     *
     * The mean:
     *
     * mean(g) = sum(g)/(2*g*N)
     *
     * The std:
     *
     * ss = 5*(1/6 + SN + S^2*N^2) + 3*g*N(1/2 +S*N) + 5*g^2*N^2/3
     *
     * std(g) = sqrt(ss/2 - mean(g))
     *
     * The median:
     *
     * median(g) = S*N
     */
//    public void testStatisticsWithConstantDegree() throws IOException, GraknValidationException {
//        int totalSteps = NUM_DIVS;
//        int nodesPerStep = MAX_SIZE/NUM_DIVS/2;
//        int v_m = totalSteps*nodesPerStep;
//        int V_m = 2*(totalSteps*nodesPerStep+1);
//
//        // detail methods that must be executed when testing
//        List<String> methods = new ArrayList<>();
//        Map<String,Function<ComputeQueryBuilderImplMock,Optional>> statisticsMethods = new HashMap<>();
//        Map<String,Consumer<Number>> statisticsAssertions = new HashMap<>();
//        methods.add("testStatisticsWithConstantDegreeSum.txt");
//        statisticsMethods.put(methods.get(0), queryBuilder -> getSumQuery(queryBuilder).of(Collections.singleton(TypeLabel.of("degree"))).execute());
//        methods.add("testStatisticsWithConstantDegreeMin.txt");
//        statisticsMethods.put(methods.get(1), queryBuilder -> getMinQuery(queryBuilder).of(Collections.singleton(TypeLabel.of("degree"))).execute());
//        methods.add("testStatisticsWithConstantDegreeMax.txt");
//        statisticsMethods.put(methods.get(2), queryBuilder -> getMaxQuery(queryBuilder).of(Collections.singleton(TypeLabel.of("degree"))).execute());
//        methods.add("testStatisticsWithConstantDegreeMean.txt");
//        statisticsMethods.put(methods.get(3), queryBuilder -> getMeanQuery(queryBuilder).of(Collections.singleton(TypeLabel.of("degree"))).execute());
//        methods.add("testStatisticsWithConstantDegreeStd.txt");
//        statisticsMethods.put(methods.get(4), queryBuilder -> getStdQuery(queryBuilder).of(Collections.singleton(TypeLabel.of("degree"))).execute());
//        methods.add("testStatisticsWithConstantDegreeMedian.txt");
//        statisticsMethods.put(methods.get(5), queryBuilder -> getMedianQuery(queryBuilder).of(Collections.singleton(TypeLabel.of("degree"))).execute());
//
//        // load up the result files
//        Map<String,CSVPrinter> printers = new HashMap<>();
//        for (String method: methods) {
//            printers.put(method,createCSVPrinter(method));
//        }
//
//        // create the ontology
//        simpleOntology(keyspace);
//
//        BatchMutatorClient loader = new BatchMutatorClient(Grakn.DEFAULT_URI, keyspace);
//
//        for (int g=1; g<totalSteps+1; g++) {
//            System.out.println("starting step: " + g);
//
//            // load data
//            System.out.println("start loading data");
//            for (int m=1; m<nodesPerStep+1; m++) {
//                loader.add(insert(var().isa("thing").has("degree", v_m)));
//                loader.add(insert(var().isa("thing").has("degree", V_m)));
//                v_m--;
//                V_m+=2;
//            }
//            loader.waitToFinish();
//            System.out.println("stop loading data");
//            GraknGraph graph = Grakn.session(Grakn.DEFAULT_URI, keyspace).open(GraknTxType.WRITE);
//            System.out.println("gremlin count is: " + graph.admin().getTinkerTraversal().count().next());
//            graph.close();
//
//            for (String method : methods) {
//                printers.get(method).print(2 * g * nodesPerStep);
//                System.out.println("starting to execute: " + method);
//                for (int workerNumber : workerNumbers) {
//                    System.out.println("starting with: " + workerNumber + " threads");
//
//                    // configure assertions
//                    final long currentG = (long) g;
//                    final long N = (long) nodesPerStep;
//                    final long S = (long) totalSteps;
//                    statisticsAssertions.put(methods.get(0), number -> {
//                        Number sum = currentG * N * (1L + currentG * N + 6L * S * N + 2L) / 2L;
//                        assertEquals(sum.doubleValue(),
//                                number.doubleValue(), 1E-9);
//                    });
//                    statisticsAssertions.put(methods.get(1), number -> {
//                        Number min = (S-currentG)*N+1L;
//                        assertEquals(min.doubleValue(),
//                                number.doubleValue(), 1E-9);
//                    });
//                    statisticsAssertions.put(methods.get(2), number -> {
//                        Number max = (S+currentG)*N*2D;
//                        assertEquals(max.doubleValue(),
//                                number.doubleValue(), 1E-9);
//                    });
//                    statisticsAssertions.put(methods.get(3), number -> {
//                        double mean = meanOfSequence(currentG, N, S);
//                        assertEquals(mean,
//                                number.doubleValue(), 1E-9);
//                    });
//                    statisticsAssertions.put(methods.get(4), number -> {
//                        double std = stdOfSequence(currentG, N, S);
//                        assertEquals(std,
//                                number.doubleValue(), 1E-9);
//                    });
//                    statisticsAssertions.put(methods.get(5), number -> {
//                        Number median = S*N;
//                        assertEquals(median.doubleValue(),
//                                number.doubleValue(), 1E-9);
//                    });
//
//                    long averageTime = 0;
//                    for (int i = 0; i < REPEAT; i++) {
//                        System.out.println("starting repeat: " + i);
//                        // check stats are correct
//                        Long startTime = System.currentTimeMillis();
//                        Number currentResult = (Number) statisticsMethods.get(method).apply(getComputeQueryBuilder(Grakn.DEFAULT_URI,keyspace,workerNumber)).get();
//                        Long stopTime = System.currentTimeMillis();
//                        averageTime += stopTime - startTime;
//                        statisticsAssertions.get(method).accept(currentResult);
//                    }
//                    averageTime /= REPEAT * 1000;
//                    printers.get(method).print(averageTime);
//                }
//                printers.get(method).println();
//                printers.get(method).flush();
//            }
//        }
//
//        for (String method : methods) {
//            printers.get(method).flush();
//            printers.get(method).close();
//        }
//        GraknGraph graph = Grakn.session(Grakn.DEFAULT_URI, keyspace).open(GraknTxType.WRITE);
//        graph.clear();
//        graph.close();
//    }

    private double meanOfSequence(long currentG, long nodesPerStep, long totalSteps) {
        return ((double) (1 + currentG * nodesPerStep + 6 * totalSteps * nodesPerStep + 2) / 4.0);
    }

    private double stdOfSequence(long currentG, long nodesPerStep, long totalSteps) {
        double mean = meanOfSequence(currentG, nodesPerStep, totalSteps);
        double S = (double) totalSteps;
        double N = (double) nodesPerStep;
        double g = (double) currentG;
        double t = 5.0*(1.0/6.0 + S*N + pow(S*N,2.0));
        t += 3.0*g*N*(1.0/2.0 + S*N);
        t += 5.0*pow(g*N,2.0)/3.0;
        return sqrt(t/2.0 - pow(mean,2.0));
    }

    private CSVPrinter createCSVPrinter(String fileName) throws IOException {
        Appendable out = new PrintWriter(fileName,"UTF-8");
        return CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])).print(out);

    }

    private void simpleOntology(GraknSession session) {
        try (GraknGraph graph = session.open(GraknTxType.WRITE)) {
            EntityType thing = graph.putEntityType("nothing");
            RoleType relation1 = graph.putRoleType("relation1");
            RoleType relation2 = graph.putRoleType("relation2");
            thing.plays(relation1).plays(relation2);
            graph.putRelationType("related").relates(relation1).relates(relation2);
            ResourceType<String> id = graph.putResourceType("node-id", ResourceType.DataType.STRING);
            thing.resource(id);
            ResourceType<Long> degree = graph.putResourceType("degree", ResourceType.DataType.LONG);
            thing.resource(degree);
            graph.commit();
        }
    }

    private Set<String> makeSuperNodes(GraknSession session) {
        Set<String> superNodes = new HashSet<>();
        try (GraknGraph graph = session.open(GraknTxType.WRITE)) {
            EntityType thing = graph.getEntityType("nothing");
            for (int i = 0; i < NUM_SUPER_NODES; i++) {
                superNodes.add(thing.addEntity().getId().getValue());
            }
            graph.commit();
        }
        return superNodes;
    }

    private void addNodesToSuperNodes(String keyspace, Set<String> superNodes, int startRange, int endRange) {
        // batch in the nodes
        BatchMutatorClient loader = new BatchMutatorClient(keyspace, engineHostname);
        loader.setNumberActiveTasks(1000);
        loader.setBatchSize(100);

        for (int nodeIndex = startRange; nodeIndex < endRange; nodeIndex++) {
            List<VarPattern> insertQuery = new ArrayList<>();
            insertQuery.add(var("node"+String.valueOf(nodeIndex)).isa("nothing"));
            for (String supernodeId : superNodes) {
                insertQuery.add(var(supernodeId).id(ConceptId.of(supernodeId)));
                insertQuery.add(var().isa("related")
                        .rel("relation1", "node"+String.valueOf(nodeIndex))
                        .rel("relation2", supernodeId));
            }
            loader.add(insert(insertQuery));
        }

        loader.waitToFinish();
    }

    private CountQueryImplMock getCountQuery(GraknGraph graph, int numWorkers) {
        return ((CountQueryImplMock) getComputeQueryBuilder(graph, numWorkers).count());
    }

    private MinQueryImplMock getMinQuery(ComputeQueryBuilderImplMock cqb) {return ((MinQueryImplMock) cqb.min());}
    private MaxQueryImplMock getMaxQuery(ComputeQueryBuilderImplMock cqb) {return ((MaxQueryImplMock) cqb.max());}
    private MeanQueryImplMock getMeanQuery(ComputeQueryBuilderImplMock cqb) {return ((MeanQueryImplMock) cqb.mean());}
    private MedianQueryImplMock getMedianQuery(ComputeQueryBuilderImplMock cqb) {return ((MedianQueryImplMock) cqb.median());}
    private SumQueryImplMock getSumQuery(ComputeQueryBuilderImplMock cqb) {return ((SumQueryImplMock) cqb.sum());}
    private StdQueryImplMock getStdQuery(ComputeQueryBuilderImplMock cqb) {return ((StdQueryImplMock) cqb.std());}

    private ComputeQueryBuilderImplMock getComputeQueryBuilder(GraknGraph graph, int numWorkers){
        return ((ComputeQueryBuilderImplMock) (new QueryBuilderImplMock(graph, numWorkers)).compute());
    }

}
