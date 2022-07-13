import scala.collection.mutable.ListBuffer

object mainApp {

  def sort[A : Ordering](coll: Seq[Iterable[A]]): Seq[Iterable[A]] = coll.sorted

  def candidates_generation(last_frequent_itemsets: List[List[Int]], k : Int) : Set[Set[Int]] = {
    val it1 = last_frequent_itemsets.iterator
    var candidates = Set[Set[Int]]()
    while (it1.hasNext) {
      val item1 = it1.next()
      val it2 = last_frequent_itemsets.iterator
      while(it2.hasNext) {
        val lNext = it2.next()
        if(item1.take(k - 2) == lNext.take(k - 2) && item1 != lNext && item1.last < lNext.last) {
          val l = List(item1 :+ lNext.last)
          val lF = l.flatten
          candidates += lF.toSet
        }
      }
    }
    // pruning
    val last_freq_itemset_Set = last_frequent_itemsets.map(_.toSet).toSet
    if(k > 2) {
      for(candidate <- candidates) {
        for(subset <- candidate.subsets(k - 1)) {
          if(!last_freq_itemset_Set.contains(subset)) {
            candidates -= candidate
          }
        }
      }
    }
    candidates
  }

  def main(args: Array[String]): Unit = {

    def filter_candidates(transactions: Iterator[Iterable[Int]], candidates: Set[Set[Int]], k: Int) : Iterator[(List[Int], Int)] = {
      var transactionSet = new ListBuffer[Set[Int]]()
      for(transaction <- transactions){
        transactionSet += transaction.toSet
      }
      val filteredItemsets = candidates.map{
        itemset => (itemset, transactionSet.count(transaction => itemset.subsetOf(transaction)))
      }.filter(x => x._2 > 0).map(x => (x._1.toList, x._2))
      filteredItemsets.iterator
    }

    val t1 = System.nanoTime // execution duration

    // Input arguments
    val ratings_path = args(0)
    val output_path = args(1)
    val n_partition = args(2).toInt
    val masterType = args(3)
    val min_support_coef = args(4).toFloat

    // SparkSession
    val spark_session = sparkSession_builder.create_SparkSession(masterType)
    val sc = spark_session.sparkContext

    // Transactions generation
    val ratings = inputHandling.readInput(spark_session, ratings_path, n_partition) // Pair RDD ("userId", "movieId")
    val transactions = ratings.aggregateByKey(List[String]())((movieId1, movieId2) => movieId2 :: movieId1, (movieId_list1, movieId_list2) => movieId_list1 ::: movieId_list2).
      map(x => x._2.map(_.toInt)) // ("movieIds")

    // Costants
    val totalTransactions = transactions.count().toInt
    val min_support = min_support_coef * totalTransactions

    // L1 generation
    val frequent_singleton = transactions.flatMap(transaction => transaction.map(movieId => (movieId, 1))).reduceByKey((x, y) => x + y).filter(x => x._2 >= min_support).map(_._1).collect()
    var l1 = ListBuffer[List[Int]]()
    for(movieId <- frequent_singleton){
      val item_as_list = List[Int](movieId)
      l1 += item_as_list
    }
    var last_frequent_itemsets = sort(l1.toList).toList.map(_.toList)
    var frequent_itemsets = ListBuffer[List[Int]]()
    frequent_itemsets ++= last_frequent_itemsets

    // Apriori
    var k = 2
    while(last_frequent_itemsets.nonEmpty) {
      val candidates = candidates_generation(last_frequent_itemsets, k)
      val candidatesPartitions = transactions.mapPartitions(x => filter_candidates(x, candidates, k))
      val new_frequent_itemsets = candidatesPartitions.reduceByKey((x,y) => x + y).filter(z => z._2 >= min_support)
      last_frequent_itemsets = sort(new_frequent_itemsets.map(x => x._1.sorted).collect().toList).toList.map(_.toList)
      frequent_itemsets ++= last_frequent_itemsets
      k = k + 1
    }

    // Output
    val duration = (System.nanoTime - t1) / 1e9d
    println("durata: " + duration.toString + " secondi\n")
    sc.parallelize(frequent_itemsets).coalesce(1).saveAsTextFile(output_path)
  }

}
