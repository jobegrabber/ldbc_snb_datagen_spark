package ldbc.snb.datagen.generation.serializer

import ldbc.snb.datagen.entities.dynamic.person.Person
import ldbc.snb.datagen.generator.generators.{GenActivity, PersonActivityGenerator}
import ldbc.snb.datagen.serializer.{DynamicActivitySerializer, PersonActivityExporter}
import ldbc.snb.datagen.generation.generator.SparkRanker
import ldbc.snb.datagen.util.{GeneratorConfiguration, SerializableConfiguration}
import ldbc.snb.datagen.syntax._
import ldbc.snb.datagen.{DatagenContext, DatagenParams}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import java.net.URI
import java.util
import java.util.Collections
import java.util.function.Consumer
import scala.collection.JavaConverters._

object SparkActivitySerializer {

  def apply(persons: RDD[Person], ranker: SparkRanker, conf: GeneratorConfiguration, partitions: Option[Int] = None, oversizeFactor: Double = 1.0)(implicit spark: SparkSession) = {

    val blockSize = DatagenParams.blockSize
    val blocks = ranker(persons)
      .map { case (k, v) => (k / blockSize, v) }
      .groupByKey()
      .pipeFoldLeft(partitions)((rdd: RDD[(Long, Iterable[Person])], p: Int) => rdd.coalesce(p))

    val serializableHadoopConf = new SerializableConfiguration(spark.sparkContext.hadoopConfiguration)

    blocks.foreachPartition(groups => {
      DatagenContext.initialize(conf)
      val partitionId = TaskContext.getPartitionId()
      val hadoopConf = serializableHadoopConf.value
      val buildDir = conf.getOutputDir

      val fs = FileSystem.get(new URI(buildDir), hadoopConf)
      fs.mkdirs(new Path(buildDir))

      val dynamicActivitySerializer = new DynamicActivitySerializer()

      dynamicActivitySerializer.initialize(fs, conf.getOutputDir, partitionId, oversizeFactor, false)

      val generator = new PersonActivityGenerator
      val exporter = new PersonActivityExporter(dynamicActivitySerializer)

      try {
        for {(blockId, persons) <- groups} {
          val clonedPersons = new util.ArrayList[Person]
          for (p <- persons) {
            clonedPersons.add(new Person(p))

            val strbuf = new StringBuilder
            for (k <- p.getKnows.iterator().asScala) {
              strbuf.append(p.getAccountId)
              strbuf.append("|")
              strbuf.append(k.to.getAccountId)
              strbuf.append("\n")
            }
          }
          Collections.sort(clonedPersons)

          val activities = generator.generateActivityForBlock(blockId.toInt, clonedPersons)

          activities.forEach(new Consumer[GenActivity] {
            override def accept(t: GenActivity): Unit = exporter.export(t)
          })
        }
      } finally {
        exporter.close()
      }
    })
  }
}
