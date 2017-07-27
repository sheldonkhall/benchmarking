package ai.grakn;

import com.ldbc.driver.DbException;
import com.ldbc.driver.ResultReporter;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery8;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcShortQuery1PersonProfile;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcShortQuery2PersonPosts;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Date;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class GraknQueryHandlersTest extends TestCase {

    GraknDbConnectionState mockConnectionState;
    ResultReporter mockReporter;
    GraknSession graknSession;


    @Before
    public void setUp() throws Exception {
        super.setUp();

        // connect to the graph
        graknSession =  Grakn.session(GraknSession.DEFAULT_URI, "snb");

        // mock the graph connection
        mockConnectionState = mock(GraknDbConnectionState.class);
        when(mockConnectionState.session()).thenReturn(graknSession);


        // mock the result reporter
        mockReporter = mock(ResultReporter.class);
    }

    @Test
    public void testQuery2Execution() throws DbException {
        // mock the query parameters
        LdbcQuery2 mockQuery = mock(LdbcQuery2.class);
        // SF1
//        when(mockQuery.personId()).thenReturn(28587302322689L);
//        when(mockQuery.maxDate()).thenReturn(Date.from(Instant.ofEpochMilli(1354060800000L)));
//        when(mockQuery.limit()).thenReturn(20);

        // validation
        when(mockQuery.personId()).thenReturn(4398046511718L);
        when(mockQuery.maxDate()).thenReturn(Date.from(Instant.ofEpochMilli(1290902400000L)));
        when(mockQuery.limit()).thenReturn(20);

        GraknQueryHandlers.LdbcQuery2Handler query2Handler = new GraknQueryHandlers.LdbcQuery2Handler();
        query2Handler.executeOperation(mockQuery,mockConnectionState,mockReporter);
    }

    @Test
    public void testQuery8Execution() throws DbException {
        LdbcQuery8 mockQuery = mock(LdbcQuery8.class);

        // validation
        when(mockQuery.personId()).thenReturn(1099511628362L);
        when(mockQuery.limit()).thenReturn(20);

        GraknQueryHandlers.LdbcQuery8Handler query8Handler = new GraknQueryHandlers.LdbcQuery8Handler();
        query8Handler.executeOperation(mockQuery,mockConnectionState,mockReporter);
    }

    @Test
    public void testShortQuery1Execution() throws DbException {
        LdbcShortQuery1PersonProfile mockQuery = mock(LdbcShortQuery1PersonProfile.class);

        // validation query
        when(mockQuery.personId()).thenReturn(2199023257132L);

        GraknShortQueryHandlers.LdbcShortQuery1PersonProfileHandler queryHandler = new GraknShortQueryHandlers.LdbcShortQuery1PersonProfileHandler();
        queryHandler.executeOperation(mockQuery, mockConnectionState, mockReporter);
    }

    @Test
    public void testShortQuery2Execution() throws DbException {
        LdbcShortQuery2PersonPosts mockQuery = mock(LdbcShortQuery2PersonPosts.class);

        // validation query
        when(mockQuery.personId()).thenReturn(2199023257132L);
        when(mockQuery.limit()).thenReturn(10);

        GraknShortQueryHandlers.LdbcShortQuery2PersonPostsHandler queryHandler = new GraknShortQueryHandlers.LdbcShortQuery2PersonPostsHandler();
        queryHandler.executeOperation(mockQuery, mockConnectionState, mockReporter);
    }

    @After
    public void tearDown() throws Exception {
        graknSession.close();
    }

}