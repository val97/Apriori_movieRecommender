import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

object sparkSession_builder {
  def create_SparkSession(master: String): SparkSession = {
    // Spark configuration
    val spark_conf = new SparkConf().setMaster(master).setAppName("movie_recommender")
    // Spark session
    val spark_session = SparkSession.builder()
      .config(spark_conf)
      .getOrCreate()
    spark_session
  }
}
