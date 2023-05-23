package wikipedia

import org.apache.spark._
import org.apache.spark.rdd.RDD


case class WikipediaArticle(title: String, text: String) {
  /**
    * @return Whether the text of this article mentions `lang` or not
    * @param lang Language to look for (e.g. "Scala")
    */
  def mentionsLanguage(lang: String): Boolean = text.split(' ').contains(lang)
}

/** Main object */
object WikipediaRanking {
  
  val langs = List(
    "JavaScript", "Java", "PHP", "Python", "C#", "C++", "Ruby", "CSS",
    "Objective-C", "Perl", "Scala", "Haskell", "MATLAB", "Clojure", "Groovy")

  // It can sometimes be useful to create a `SparkConf` object to store all the 
  // configuration parameter values for our Spark context, rather than inserting 
  // all the options in the `SparkContext` constructor.
  val conf: SparkConf = new SparkConf().setMaster("local").setAppName("Wikipedia Ranking")

  // Then, to create the Spark context, we can simply pass the `SparkConf` object 
  // to the `SparkContext` constructor.
  val sc: SparkContext = new SparkContext(conf)



  /** Main function */
  def main (args: Array[String]) : Unit = {
    
    // Languages ranked according to (1)
    val langsRanked: List[(String, Int)] =
      timed("Part 1: naive ranking", rankLangs(langs, wikiRdd))

    // An inverted index mapping languages to wikipedia pages on which they appear
    def index: RDD[(String, Iterable[WikipediaArticle])] = makeIndex(langs, wikiRdd)

    // Languages ranked according to (2), using the inverted index
    val langsRanked2: List[(String, Int)]
      = timed("Part 2: ranking using inverted index", rankLangsUsingIndex(index))

    // Languages ranked according to (3)
    val langsRanked3: List[(String, Int)]
      = timed("Part 3: ranking using reduceByKey", rankLangsReduceByKey(langs, wikiRdd))

    // Output the speed of each ranking
    println(timing)
    sc.stop()

  }

  // TASK 1 //////////////////////////////////////////////////////////////////////

  val wikiRdd: RDD[WikipediaArticle] = sc.textFile(WikipediaData.filePath).map(WikipediaData.parse)
  


  // TASK 2 //////////////////////////////////////////////////////////////////////

  // TASK 2: attempt #1 ----------------------------------------------------------

  /** Returns the number of articles in which the language `lang` occurs.
   */
  def occurrencesOfLang(lang: String, rdd: RDD[WikipediaArticle]): Int = {
    def f(acc:Int, articles:WikipediaArticle):Int=
       if (articles.mentionsLanguage(lang)) acc+1
       else acc
     rdd.aggregate(0)(f,_+_)
     }
    


  /** Uses `occurrencesOfLang` to compute the ranking of the languages
    * (`val langs`) by determining the number of Wikipedia articles that
    * mention each language at least once.
    *
    * IMPORTANT: The result is sorted by number of occurrences, in descending order.
    */
  def rankLangs(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = {
    val result =
      langs
        .map(lang => (lang, occurrencesOfLang(lang, rdd)))
        .sortWith(_._2 > _._2)
    result
  }

  // TASK 2: attempt #2 ----------------------------------------------------------

  /** Computes an inverted index of the set of articles, mapping each language
    * to the Wikipedia pages in which it occurs.
    */
  def makeIndex(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])] = {
  
   

    // You need not do this is small steps.  You can write a one line program.
    // However, if you would like to break up the task into small parts, here
    // is what I would recommend.
    
    // 1. First, create a collection of all pairs (l, wa), where l is a 
    //    language and wa is a Wikipedia article.
    // val pairs: RDD[(String,WikipediaArticle)] = ???
    val pairs: RDD[(String, WikipediaArticle)] = rdd.flatMap(article => {
    langs.map(lang => (lang, article))
    })
    // 2. Second, filter the `pairs` RDD to create a collection of all 
    //    pairs (l, wa) where wa is an article that mentions language l.
    // val mentionedPairs: RDD[(String,WikipediaArticle)] = ???
    val mentionedPairs: RDD[(String, WikipediaArticle)] = pairs.filter(pair => {
    pair._2.mentionsLanguage(pair._1)
    })
   // 3. Finally, return the result of performing a "group by" on `mentionedPairs`
    //    which yields key-value pairs where the key is a language `l` and value is 
    //    the collection of wikipedia articles that mention `l`.
   val result: RDD[(String, Iterable[WikipediaArticle])] = mentionedPairs.groupByKey()
    
    // <<<<  replace ??? with what you want the `makeIndex` function to return.
  
    result
  }
  // Computes the language ranking using the inverted index.
  def rankLangsUsingIndex(index: RDD[(String, Iterable[WikipediaArticle])]): List[(String, Int)] = {
    val result = for (idx <- index) yield (
      idx._1, idx._2.toArray.length
    )
    result.collect.toList.sortWith(_._2 > _._2)
  }
  

  // TASK 2: attempt #3 ----------------------------------------------------------

  // You might find the helper function `zipLangWithPoint` useful, but it's also possible
  // to complete this part of the assignment with just the `rankLangsReduceByKey` function.

  // Return a list of `(lang, integer)` pairs containing one pair for each article mentioning `lang`.
  // def zipLangWithPoint(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Int)] = ???
  def zipLangWithPoint(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Int)] = {
    rdd.flatMap(article => langs
       .filter(lang => article.mentionsLanguage(lang))
       .map(lang => (lang, 1)))
  }
  /** Uses `reduceByKey` to compute the index and the ranking simultaneously.
    */


  def rankLangsReduceByKey(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = {
    zipLangWithPoint(langs, rdd)
      .reduceByKey((count1, count2) => count1 + count2)
      .sortBy(pair => -pair._2)
      .collect()
      .toList
  }


  //---- Do not edit below this line -------------------------------------------------------------
  val timing = new StringBuffer
  def timed[T](label: String, code: => T): T = {
    val start = System.currentTimeMillis()
    val result = code
    val stop = System.currentTimeMillis()
    timing.append(s"Processing $label took ${stop - start} ms.\n")
    result
  }

}

