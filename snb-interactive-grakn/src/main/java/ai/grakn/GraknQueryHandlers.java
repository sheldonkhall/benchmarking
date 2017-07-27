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
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery8Result;

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
    static VarPattern replyOf = var().label("reply-of");
    static VarPattern reply = var().label("reply");

    static Label personID = Label.of("person-id");
    static Label messageDate = Label.of("creation-date");
    static Label messageID = Label.of("message-id");
    static Label personFirstName = Label.of("first-name");
    static Label personLastName = Label.of("last-name");
    static Label messageContent = Label.of("content");
    static Label messageImageFile = Label.of("image-file");

    static Var thePerson = var("person");
    static Var aMessage = var("aMessage");
    static Var aMessageDate = var("aMessageDate");
    static Var aMessageId = var("aMessageID");
    static Var someContent = var("content");

    // method to get the value of a resource from an answer
    private static <T> T resource(Answer result, Var resource) {
        return result.get(resource).<T>asResource().getValue();
    }

    private static long timeResource(Answer result, Var resource) {
        return result.get(resource).<LocalDateTime>asResource().getValue().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    public static class LdbcQuery2Handler implements OperationHandler<LdbcQuery2, GraknDbConnectionState> {

        @Override
        public void executeOperation(LdbcQuery2 ldbcQuery2, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                Var aFriend = var("aFriend");
                Var aFriendId = var("aFriendID");
                Var aFriendFirstName = var("aFriendFirstName");
                Var aFriendLastName = var("aFriendLastName");
                LocalDateTime maxDate = LocalDateTime.ofInstant(ldbcQuery2.maxDate().toInstant(), ZoneOffset.UTC);

                // to make this query execute faster split it into two parts:
                //     the first does the ordering
                //     the second fetches the resources
                MatchQuery graknLdbcQuery2 = match(
                        var().rel(thePerson.has(personID, var().val(ldbcQuery2.personId()))).rel(aFriend).isa(knowsType),
                        var().rel(aFriend).rel(aMessage).isa(hasCreatorType),
                        aMessage.has(messageDate, aMessageDate).has(messageID, aMessageId),
                        aMessageDate.val(lte(maxDate)));

                List<Answer> rawResult = graknLdbcQuery2.orderBy(aMessageDate, Order.desc)
                        .limit(ldbcQuery2.limit()).withGraph(graknGraph).execute();

                // sort first by date and then by message id
                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(
                        map -> timeResource(map, aMessageDate)).reversed().
                        thenComparingLong(map -> resource(map, aMessageId));

                // process the query results
                List<LdbcQuery2Result> result = rawResult.stream().sorted(ugly).map(map -> {
                    // fetch the resources attached to entities in the queries
                    MatchQuery queryExtendedInfo = match(
                            aFriend.has(personFirstName, aFriendFirstName).has(personLastName, aFriendLastName).has(personID, aFriendId),
                            var().rel(aFriend).rel(aMessage).isa(hasCreatorType),
                            aMessage.has(messageDate, aMessageDate)
                                    .has(messageID, var().val(GraknQueryHandlers.<Long>resource(map, aMessageId))),
                            or(aMessage.has(messageContent, someContent), aMessage.has(messageImageFile, someContent)));
                    Answer extendedInfo = queryExtendedInfo.withGraph(graknGraph).execute().iterator().next();

                    // prepare the answer from the original query and the query for extended information
                    return new LdbcQuery2Result(
                            resource(extendedInfo, aFriendId),
                            resource(extendedInfo, aFriendFirstName),
                            resource(extendedInfo, aFriendLastName),
                            resource(map, aMessageId),
                            resource(extendedInfo, someContent),
                            timeResource(map, aMessageDate));
                }).collect(Collectors.toList());

                resultReporter.report(0,result,ldbcQuery2);
            }
        }

    }

    public static class LdbcQuery8Handler implements OperationHandler<LdbcQuery8, GraknDbConnectionState> {
        @Override
        public void executeOperation(LdbcQuery8 ldbcQuery8, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                // for speed the query is again split into the ordering and limit phase
                Var aReply = var("aReply");
                Var responder = var("responder");
                Var responderId = var("responderId");
                Var responderFirst = var("responderFirst");
                Var responderLast = var("responderLast");
                MatchQuery orderQuery = match(
                        thePerson.has(personID, var().val(ldbcQuery8.personId())),
                        var().rel(thePerson).rel(aMessage).isa(hasCreatorType),
                        var().rel(aMessage).rel(reply, aReply).isa(replyOf),
                        aReply.has(messageDate, aMessageDate).has(messageID, aMessageId)
                );
                List<Answer> rawResult = orderQuery.withGraph(graknGraph)
                        .orderBy(aMessageDate, Order.desc).limit(ldbcQuery8.limit()).execute();

                // sort first by date and then by message id
                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(
                        map -> timeResource(map, aMessageDate)).reversed().
                        thenComparingLong(map -> resource(map, aMessageId));

                // process the query results
                List<LdbcQuery8Result> result = rawResult.stream().sorted(ugly).map(map -> {
                    // fetch the resources attached to entities in the queries
                    MatchQuery queryExtendedInfo = match(
                            aReply.has(messageID, var().val(GraknQueryHandlers.<Long>resource(map, aMessageId))),
                            or(aReply.has(messageContent, someContent), aReply.has(messageImageFile, someContent)),
                            var().rel(aReply).rel(responder).isa(hasCreatorType),
                            responder.has(personID, responderId).has(personFirstName, responderFirst).has(personLastName, responderLast)
                            );
                    Answer extendedInfo = queryExtendedInfo.withGraph(graknGraph).execute().iterator().next();

                    // prepare the answer from the original query and the query for extended information
                    return new LdbcQuery8Result(
                            resource(extendedInfo, responderId),
                            resource(extendedInfo, responderFirst),
                            resource(extendedInfo, responderLast),
                            timeResource(map, aMessageDate),
                            resource(map, aMessageId),
                            resource(extendedInfo, someContent));
                }).collect(Collectors.toList());

                resultReporter.report(0,result,ldbcQuery8);
            }
        }
    }
}
