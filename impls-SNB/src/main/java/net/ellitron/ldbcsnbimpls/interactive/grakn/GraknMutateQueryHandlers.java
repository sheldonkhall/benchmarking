package net.ellitron.ldbcsnbimpls.interactive.grakn;

import ai.grakn.GraknGraph;
import ai.grakn.GraknSession;
import ai.grakn.GraknTxType;
import ai.grakn.concept.Concept;
import ai.grakn.graql.MatchQuery;
import ai.grakn.graql.admin.Answer;
import com.ldbc.driver.DbException;
import com.ldbc.driver.OperationHandler;
import com.ldbc.driver.ResultReporter;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcShortQuery3PersonFriends;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcShortQuery3PersonFriendsResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class implements various queries, but changes the behaviour to include a deletion.
 */
public class GraknMutateQueryHandlers {

    public static class LdbcShortQuery3PersonFriendsHandler implements
            OperationHandler<LdbcShortQuery3PersonFriends, GraknDbConnectionState> {

        @Override
        public void executeOperation(LdbcShortQuery3PersonFriends operation,
                                     GraknDbConnectionState dbConnectionState,
                                     ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graph = session.open(GraknTxType.READ)) {

                String query = "match " +
                        "$person isa person has person-id " + operation.personId() + "; " +
                        "(friend: $person, friend: $friend) isa knows has creation-date $date; " +
                        "$friend has person-id $friendId has first-name $fname has last-name $lname; ";


                List<Answer> results = graph.graql().<MatchQuery>parse(query).execute();


                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(map -> resource(map, "date")).reversed()
                        .thenComparingLong(map -> resource(map, "friendId"));

                List<LdbcShortQuery3PersonFriendsResult> result = results.stream()
                        .sorted(ugly)
                        .map(map -> new LdbcShortQuery3PersonFriendsResult(resource(map, "friendId"),
                                resource(map, "fname"),
                                resource(map, "lname"),
                                resource(map, "date")))
                        .collect(Collectors.toList());

                resultReporter.report(0, result, operation);

            }
        }

        private <T> T resource(Answer result, String name) {
            return result.get(name).<T>asResource().getValue();
        }
    }
}
