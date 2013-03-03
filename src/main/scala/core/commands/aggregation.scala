package core.commands

import reactivemongo.bson._
import reactivemongo.bson.BSONString
import reactivemongo.core.commands.{CommandError, BSONCommandResultMaker, Command}

/**
 * Implements the "aggregation" command, otherwise known as the "Aggregation Framework."
 * http://docs.mongodb.org/manual/applications/aggregation/
 *
 * @param collectionName Collection to aggregate against
 * @param pipeline Sequence of MongoDB aggregation operations.
 */
case class Aggregate (
  collectionName: String,
  pipeline: Seq[PipelineOperator]
) extends Command[BSONValue] {
  override def makeDocuments =
    BSONDocument(
      "aggregate" -> BSONString(collectionName),
      "pipeline" -> BSONArray(
        {for (pipe <- pipeline) yield pipe.makePipe} : _*
      )
    )

  val ResultMaker = Aggregate
}

object Aggregate extends BSONCommandResultMaker[BSONValue] {
  def apply(document: TraversableBSONDocument) =
    CommandError.checkOk(document, Some("aggregate")).toLeft(document.get("result").get)
}

/**
 * One of MongoDBs pipeline operators for aggregation. Sealed as these are defined in
 * the mongodb spec, and clients should not have custom operators.
 */
sealed trait PipelineOperator {
  def makePipe: BSONValue
}

/**
 * Reshapes a document stream by renaming, adding, or removing fields.
 * Also use "Project" to create computed values or sub-objects.
 * NOTE: Currently only supports removing fields fields.
 * http://docs.mongodb.org/manual/reference/aggregation/project/#_S_project
 * @param fields Fields to include. The resulting objects will contain only these fields
 */
case class Project(fields: String*) extends PipelineOperator{
  override val makePipe = BSONDocument("$project" -> BSONDocument(
    {for (field <- fields) yield field -> BSONInteger(1)} : _*
  ))
}

/**
 * Filters out documents from the stream that do not match the predicate.
 * http://docs.mongodb.org/manual/reference/aggregation/match/#_S_match
 * @param predicate Query that documents must satisfy to be in the stream.
 */
case class Match(predicate: BSONDocument) extends PipelineOperator{
  override val makePipe = BSONDocument("$match" -> predicate)
}

/**
 * Limts the number of documents that pass through the stream.
 * http://docs.mongodb.org/manual/reference/aggregation/limit/#_S_limit
 * @param limit Number of documents to allow through.
 */
case class Limit(limit: Int) extends PipelineOperator{
  override val makePipe = BSONDocument("$limit" -> BSONInteger(limit))
}

/**
 * Skips over a number of documents before passing all further documents along the stream.
 * http://docs.mongodb.org/manual/reference/aggregation/skip/#_S_skip
 * @param skip Number of documents to skip.
 */
case class Skip(skip: Int) extends PipelineOperator{
  override val makePipe = BSONDocument("$skip" -> BSONInteger(skip))
}

/**
 * Turns a document with an array into multiple documents, one document for each
 * element in the array.
 * http://docs.mongodb.org/manual/reference/aggregation/unwind/#_S_unwind
 * @param field Name of the array to unwind.
 */
case class Unwind(field: String) extends PipelineOperator{
  override val makePipe = BSONDocument("$unwind" -> BSONString("$" + field))
}

/**
 * Groups documents together to calulate aggregates on document collections. This command
 * aggregates on one field.
 * http://docs.mongodb.org/manual/reference/aggregation/group/#_S_group
 * @param idField Name of the field to aggregate on.
 * @param ops Sequence of operators specifying aggregate calculation.
 */
case class GroupField(idField: String)(ops: (String, GroupFunction)*) extends PipelineOperator {
  override val makePipe = BSONDocument(
    "$group" -> BSONDocument(
    {"_id" -> BSONString(idField)}
      +: {ops.map{
        case (field, operator) => field -> operator.makeFunction
      }}:_*
    )
  )
}

/**
 * Groups documents together to calulate aggregates on document collections. This command
 * aggregates on multiple fields, and they must be named.
 * http://docs.mongodb.org/manual/reference/aggregation/group/#_S_group
 * @param idField Fields to aggregate on, and the names they should be aggregated under.
 * @param ops Sequence of operators specifying aggregate calculation.
 */
case class GroupMulti(idField: (String, String)*)(ops: (String, GroupFunction)*) extends PipelineOperator {
  override val makePipe = BSONDocument(
    "$group" -> BSONDocument(
      {"_id" -> BSONDocument(
        idField.map{
          case (alias, attribute) => alias -> BSONString("$" + attribute)
        }:_*
      )} +:
      {ops.map{
        case (field, operator) => field -> operator.makeFunction
      }}:_*
    )
  )
}

/**
 * Sorts the stream based on the given fields.
 * http://docs.mongodb.org/manual/reference/aggregation/sort/#_S_sort
 * @param fields Fields to sort by.
 */
case class Sort(fields: Seq[SortOrder]) extends PipelineOperator{
  override val makePipe = BSONDocument("$sort" -> BSONDocument(fields.map{
    case Ascending(field) => field -> BSONInteger(1)
    case Descending(field) => field -> BSONInteger(-1)
  } : _*))
}

/**
 * Represents that a field should be sorted on, as well as whether it
 * should be ascending or descending.
 */
sealed trait SortOrder
case class Ascending(field: String) extends SortOrder
case class Descending(field: String) extends SortOrder

/**
 * Represents one of the group operators for the "Group" Operation. This class is sealed
 * as these are defined in the MongoDB spec, and clients should not need to customise these.
 */
sealed trait GroupFunction {
  def makeFunction: BSONValue
}

case class AddToSet(field: String) extends GroupFunction{
  def makeFunction = BSONDocument("$addToSet" -> BSONString(field))
}

case class First(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$first" -> BSONString("$" + field))
}

case class Last(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$last" -> BSONString("$" + field))
}

case class Max(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$max" -> BSONString("$" + field))
}

case class Min(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$min" -> BSONString("$" + field))
}

case class Avg(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$avg" -> BSONString("$" + field))
}

case class Push(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$push" -> BSONString("$" + field))
}

case class SumField(field: String) extends GroupFunction {
  def makeFunction = BSONDocument("$sum" -> BSONString("$" + field))
}

case class SumValue(value: Int) extends GroupFunction {
  def makeFunction = BSONDocument("$sum" -> BSONInteger(value))
}