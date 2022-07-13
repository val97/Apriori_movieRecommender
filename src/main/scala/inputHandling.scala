import org.apache.spark.HashPartitioner
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object inputHandling {
  def readInput(spark_session: SparkSession, ratings_path: String, n_partition: Int): RDD[(String, String)] = {
    // load dataset ratings
    val ds_ratings = spark_session.sparkContext.textFile(ratings_path)
    val header = ds_ratings.first()
    val ratingsRDD = ds_ratings.
      filter(line => line != header).
      map(f => {
        f.split(",")
      }).
      filter(x => x(2).toFloat >= 3).
      map(x => (x(0), x(1))).
      partitionBy(new HashPartitioner(n_partition))

    ratingsRDD
  }
}
