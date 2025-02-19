package ldbc.snb.datagen

import ldbc.snb.datagen.syntax._
import ldbc.snb.datagen.util.camel
import org.apache.spark.sql.{Column, DataFrame}

import scala.language.higherKinds

package object model {
  type Id[A] = A
  sealed trait Cardinality
  object Cardinality {
    case object OneN extends Cardinality
    case object NN extends Cardinality
    case object NOne extends Cardinality
  }

  sealed trait EntityType {
    val isStatic: Boolean
    val entityPath: String
    val primaryKey: Seq[String]
  }

  object EntityType {
    private def s(isStatic: Boolean) = if (isStatic) "static" else "dynamic"

    final case class Node(name: String, isStatic: Boolean = false) extends EntityType {
      override val entityPath: String = s"${s(isStatic)}/${name}"
      override val primaryKey: Seq[String] = Seq("id")
      override def toString: String = s"$name"
    }

    final case class Edge(
      `type`: String,
      source: String,
      destination: String,
      cardinality: Cardinality,
      isStatic: Boolean = false
    ) extends EntityType {
      override val entityPath: String = s"${s(isStatic)}/${source}_${camel(`type`)}_${destination}"

      override val primaryKey: Seq[String] = ((source, destination) match {
          case (s, d) if s == d => Seq(s"${s}1", s"${d}2")
          case (s, d) => Seq(s, d)
      }).map(name => s"${name}Id")

      override def toString: String = s"$source -[${`type`}]-> $destination"
    }

    final case class Attr(`type`: String, parent: String, attribute: String, isStatic: Boolean = false) extends EntityType {
      override val entityPath: String = s"${s(isStatic)}/${parent}_${camel(`type`)}_${attribute}"

      override val primaryKey: Seq[String] = ((parent, attribute) match {
        case (s, d) if s == d => Seq(s"${s}1", s"${d}2")
        case (s, d) => Seq(s, d)
      }).map(name => s"${name}Id")
      override def toString: String = s"$parent ♢-[${`type`}]-> $attribute"
    }

  }

  case class Batched(entity: DataFrame, batchId: Seq[String])

  case class BatchedEntity(
    snapshot: DataFrame,
    insertBatches: Option[Batched],
    deleteBatches: Option[Batched]
  )

  sealed trait Mode {
    type Layout
    def modePath: String
  }
  object Mode {
    final case object Raw extends Mode {
      type Layout = DataFrame
      override val modePath: String = "raw"

      def withRawColumns(et: EntityType, cols: Column*): Seq[Column] = (!et.isStatic).fork.foldLeft(cols)((cols, _) => Seq(
        $"creationDate".as("creationDate"),
        $"deletionDate".as("deletionDate"),
        $"explicitlyDeleted".as("explicitlyDeleted")
      ) ++ cols)

      def dateTimePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00"
      def datePattern = "yyyy-MM-dd"

    }
    final case class Interactive(bulkLoadPortion: Double) extends Mode {
      type Layout = DataFrame
      override val modePath: String = "interactive"
    }
    final case class BI(bulkloadPortion: Double, batchPeriod: String) extends Mode {
      type Layout = BatchedEntity
      override val modePath: String = "bi"
    }
  }

  trait GraphLike[+M <: Mode] {
    def isAttrExploded: Boolean
    def isEdgesExploded: Boolean
    def mode: M
  }

  case class Graph[+M <: Mode](
    isAttrExploded: Boolean,
    isEdgesExploded: Boolean,
    mode: M,
    entities: Map[EntityType, M#Layout]
  ) extends GraphLike[M]

  case class GraphDef[M <: Mode](
    isAttrExploded: Boolean,
    isEdgesExploded: Boolean,
    mode: M,
    entities: Map[EntityType, Option[String]]
  ) extends GraphLike[M]

  sealed trait BatchPeriod
  object BatchPeriod {
    case object Day extends BatchPeriod
    case object Month extends BatchPeriod
  }
}