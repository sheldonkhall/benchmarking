package ai.grakn;

import ai.grakn.concept.Label;
import ai.grakn.graql.Graql;
import ai.grakn.graql.MatchQuery;
import ai.grakn.graql.Order;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.admin.Answer;
import com.ldbc.driver.DbException;
import com.ldbc.driver.OperationHandler;
import com.ldbc.driver.ResultReporter;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2Result;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery8;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static ai.grakn.graql.Graql.lte;
import static ai.grakn.graql.Graql.match;
import static ai.grakn.graql.Graql.or;
import static ai.grakn.graql.Graql.var;

/**
 *
 */
public class GraknQueryHandlers {

    static VarPattern knowsType = var().label("knows");
    static VarPattern hasCreatorType = var().label("has-creator");

    static Label personID = Label.of("person-id");
    static Label messageDate = Label.of("creation-date");
    static Label messageID = Label.of("message-id");
    static Label personFirstName = Label.of("first-name");
    static Label personLastName = Label.of("last-name");
    static Label messageContent = Label.of("content");
    static Label messageImageFile = Label.of("image-file");

    public static class LdbcQuery2Handler implements OperationHandler<LdbcQuery2, GraknDbConnectionState> {

        @Override
        public void executeOperation(LdbcQuery2 ldbcQuery2, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                Var thePerson = var("person");
                Var aFriend = var("aFriend");
                Var aFriendId = var("aFriendID");
                Var aFriendFirstName = var("aFriendFirstName");
                Var aFriendLastName = var("aFriendLastName");
                Var aMessage = var("aMessage");
                Var aMessageDate = var("aMessageDate");
                Var aMessageId = var("aMessageID");
                Var someContent = var("content");
                LocalDateTime maxDate = LocalDateTime.ofInstant(ldbcQuery2.maxDate().toInstant(), ZoneOffset.UTC);

                // to make this query execute faster split it into two parts:
                //     the first does the ordering
                //     the second fetches the resources
                MatchQuery graknLdbcQuery2 = match(
                        var().rel(thePerson.has(personID, var().val(ldbcQuery2.personId()))).rel(aFriend).isa(knowsType),
                        var().rel(aFriend).rel(aMessage).isa(hasCreatorType),
                        aMessage.has(messageDate, aMessageDate).has(messageID, aMessageId),
                        aMessageDate.val(lte(maxDate)));

                List<Answer> rawResult = graknLdbcQuery2.orderBy(aMessageDate, Order.desc).limit(ldbcQuery2.limit()).withGraph(graknGraph).execute();

                // sort first by date and then by message id
                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(
                        map -> map.get(aMessageDate).<LocalDateTime>asResource().getValue().
                                toInstant(ZoneOffset.UTC).toEpochMilli()).reversed().
                        thenComparingLong(map -> resource(map, aMessageId));

                // process the query results
                List<LdbcQuery2Result> result = rawResult.stream().sorted(ugly).map(map -> {
                    // fetch the resources attached to entities in the queries
                    MatchQuery queryExtendedInfo = match(
                            aFriend.has(personFirstName, aFriendFirstName).has(personLastName, aFriendLastName).has(personID, aFriendId),
                            var().rel(aFriend).rel(aMessage).isa(hasCreatorType),
                            aMessage.has(messageDate, aMessageDate)
                                    .has(messageID, var().val(this.<Long>resource(map, aMessageId))),
                            or(aMessage.has(messageContent, someContent), aMessage.has(messageImageFile, someContent)));
                    Answer extendedInfo = queryExtendedInfo.withGraph(graknGraph).execute().iterator().next();

                    // prepare the answer from the original query and the query for extended information
                    return new LdbcQuery2Result(
                            resource(extendedInfo, aFriendId),
                            resource(extendedInfo, aFriendFirstName),
                            resource(extendedInfo, aFriendLastName),
                            resource(map, aMessageId),
                            resource(extendedInfo, someContent),
                            map.get(aMessageDate).<LocalDateTime>asResource().getValue().
                                    toInstant(ZoneOffset.UTC).toEpochMilli());
                }).collect(Collectors.toList());

                result.iterator().forEachRemaining(System.out::println);
                resultReporter.report(0,result,ldbcQuery2);
            }
        }

        private <T> T resource(Answer result, Var resource) {
            return result.get(resource).<T>asResource().getValue();
        }
    }

    public static class LdbcQuery8Handler implements OperationHandler<LdbcQuery8, GraknDbConnectionState> {
        @Override
        public void executeOperation(LdbcQuery8 ldbcQuery8, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {

        }
    }
}
