package timeusage

import org.apache.commons.collections.functors.TruePredicate
import org.apache.spark.sql._
import org.apache.spark.sql.expressions.Aggregator
import org.apache.spark.sql.types._
import java.io.File


/** Main class */
object TimeUsage {

  import org.apache.spark.sql.SparkSession
  import org.apache.spark.sql.functions._

  val spark: SparkSession =
    SparkSession
      .builder()
      .appName("Time Usage")
      .config("spark.master", "local")
      .getOrCreate()

  // For implicit conversions like converting RDDs to DataFrames
  import spark.implicits._

  /** Main function */
  def main (args: Array[String]) : Unit =  {

    val (columns, initDf) = read("timeusage/atussum.csv")
    val (primaryNeedsColumns, workColumns, otherColumns) = classifiedColumns(columns)
    val summaryDf = timeUsageSummary(primaryNeedsColumns, workColumns, otherColumns, initDf)
    val finalDf = timeUsageGrouped(summaryDf)
    finalDf.show()
  }


  /**
  * Models a row of the summarized data set
  * @param working Working status (either "working" or "not working")
  * @param sex Sex (either "male" or "female")
  * @param age Age (either "young", "active" or "elder")
  * @param primaryNeeds Number of daily hours spent on primary needs
  * @param work Number of daily hours spent on work
  * @param other Number of daily hours spent on other activities
  */
  case class TimeUsageRow( working: String, sex: String, age: String
                         , primaryNeeds: Double, work: Double, other: Double )


  /** @return The schema of the DataFrame, assuming that the first given
    *         column has type String and all the others have type Double.
    *         None of the fields are nullable.
    * @param columnNames Column names of the DataFrame
    */
  def dfSchema(columnNames: List[String]): StructType = {
    val headField = StructField(columnNames.head, StringType, false)
    val tailFields = columnNames.tail.map(cn => StructField(cn, DoubleType, false))
    StructType(headField :: tailFields)
  }


  /** @return An RDD Row compatible with the schema produced by `dfSchema`
    * @param line Raw fields
    */
  def row(line: List[String]): Row ={
    Row.fromSeq(line.head :: line.tail.map(x=>x.toDouble))
  } 



  /** @return a pair (cn, df) where df is a DataFrame and cn holds the column names of df. */
  def read(resource: String): (List[String], DataFrame) = {
    import org.apache.log4j.Logger
    import org.apache.log4j.Level

    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val rdd = spark.sparkContext.textFile(fsPath(resource))
    val cn = rdd.first().split(",").toList

    // Compute the schema based on the first line of the CSV file.
    val schema = dfSchema(cn)

    // Create pair RDD of (index, data) skipping the first (header) line.
    val index_data = rdd.mapPartitionsWithIndex((i, it) => if (i == 0) it.drop(1) else it) // skip the header line
                        .map(_.split(",").toList)
                        .map(row)

    // Construct DataFrame from index_data pair RDD and schema.
    val df = spark.createDataFrame(index_data, schema)

    (cn, df) // return column names and DataFrame
  }


  /** @return The filesystem path of the given resource */
  def fsPath(resource: String): String = {
    // Paths.get(getClass.getResource(resource).toURI).toString
    val r = this.getClass.getClassLoader.getResource(resource)
    if (r == null) sys.error(   "\n/////////////////////////////////////////////////////////////////"
                              + "\n//                                                             //"
                              + "\n//                 !!! ATTENTION !!!                           //"
                              + "\n//                                                             //"
                              + "\n//  You must download the dataset file `atussum.csv.bz2`,      //"
                              + "\n//  unzip it, and put it in the `src/main/resources/timeusage` //"
                              + "\n//  directory, as explained in the project instructions.       //"
                              + "\n//                                                             //"
                              + "\n/////////////////////////////////////////////////////////////////")
    new File(r.toURI).getPath

  }  

  /** @return The initial data frame columns partitioned in three groups:
    *         primary needs (sleeping, eating, etc.), work and other (leisure activities)
    *
    * @see https://www.kaggle.com/bls/american-time-use-survey
    *
    * The dataset contains the daily time (in minutes) people spent in various activities.
    * For instance, the column "t010101" contains the time spent sleeping, the column
    * "t110101" contains the time spent eating and drinking, etc.
    *
    * This method groups related columns together:
    * 1. "primary needs" activities (sleeping, eating, etc.). These are the columns starting
    *    with "t01", "t03", "t11", "t1801" and "t1803".
    * 2. working activities. These are the columns starting with "t05" and "t1805".
    * 3. other activities (leisure). These are the columns starting with "t02", "t04", "t06", "t07", "t08", "t09",
    *    "t10", "t12", "t13", "t14", "t15", "t16" and "t18" (those which are not part of the previous groups only).
    */
  def classifiedColumns(columnNames: List[String]): (List[Column], List[Column], List[Column]) ={
    
    val pnPrefixes = List ("t1", "t03", "t11", "t1801", "t1803")
    val waPrefixes = List ( "t05", "t1805") 
    val oaPrefixes = List("t02", "t04", "t06", "t07", "t08", "t09", "t10", "t12", "t13", "t14", "t15", "t16", "t18")
    val pn = columnNames.filter (cn => pnPrefixes.exists(s => cn.startsWith(s))).map (s => col(s)) // { YOUR CODE HERE }
    val wa = columnNames.filter (cn => waPrefixes.exists(s => cn.startsWith(s))).map (s => col(s))
    val oa = columnNames.filter (cn => oaPrefixes.exists(s => cn.startsWith(s))
                                         && !(cn.startsWith("t1801"))
                                         && !(cn.startsWith("t1803"))
                                         && !(cn.startsWith("t1805"))
                                ) .map (s => col(s))

    return (pn, wa, oa)
  }

  /** @return a projection of the initial DataFrame such that all columns containing hours spent on primary needs
    *         are summed together in a single column (and same for work and leisure). The "teage" column is also
    *         projected to three values: "young", "active", "elder".
    *
    * @param primaryNeedsColumns List of columns containing time spent on "primary needs"
    * @param workColumns List of columns containing time spent working
    * @param otherColumns List of columns containing time spent doing other activities
    * @param df DataFrame whose schema matches the given column lists
    *
    * This methods builds an intermediate DataFrame that sums up all the columns of each group of activity into
    * a single column.
    *
    * The resulting DataFrame should have the following columns:
    * - working: value computed from the "telfs" column of the given DataFrame:
    *   - "working" if 1 <= telfs < 3
    *   - "not working" otherwise
    * - sex: value computed from the "tesex" column of the given DataFrame:
    *   - "male" if tesex = 1, "female" otherwise
    * - age: value computed from the "teage" column of the given DataFrame:
    *   - "young" if 15 <= teage <= 22,
    *   - "active" if 23 <= teage <= 55,
    *   - "elder" otherwise
    * - primaryNeeds: sum of all the `primaryNeedsColumns`, in hours
    * - work: sum of all the `workColumns`, in hours
    * - other: sum of all the `otherColumns`, in hours
    *
    * Finally, the resulting DataFrame should exclude people that are not employable (ie telfs = 5).
    *
    * Note that the initial DataFrame contains time in ''minutes''. You have to convert it into ''hours''.
    */
  def timeUsageSummary( primaryNeedsColumns : List[Column]
                      , workColumns         : List[Column]
                      , otherColumns        : List[Column]
                      , df                  : DataFrame
                      ) : DataFrame = {
                         
    // Transform the data from the initial dataset into data that make more sense for our use case.
    // Hints: 1. use `when` and `otherwise`
    //        2. don't forget to give columns the expected names; e.g. `.as("workingStatusProjection")`
      
    val workingStatusProjection: Column = when(df("telfs")>=1 && df("telfs")<3,"working")   // { YOUR CODE HERE }
                                          .otherwise("not working")
                                          .as("workingStatusProjection")

    
    val sexProjection: Column = when(df("tesex")===1,"male")    // { YOUR CODE HERE }
                                .otherwise("female")
                                .as("sexProjection")

    
    val ageProjection: Column = when(df("teage")>=15 && df("teage")<=22,"young")     // { YOUR CODE HERE }
                                .when(df("teage")>=23 && df("teage")<=55,"active")
                                .otherwise("elder")
                                .as("ageProjection")
      

    // Create columns that sum columns of the initial dataset
    // Hints: 1. use the `+` operator to create complex column expressions that sum other columns
    //        2. don't forget to convert the value to hours

    val primaryNeeds: Column = primaryNeedsColumns.reduce(_+_).divide(60.0).as("primaryNeeds")   // { YOUR CODE HERE }
                                                
      
    val work: Column = workColumns.reduce(_+_).divide(60.0).as("work")
                                   // { YOUR CODE HERE }
                                  
      
    val other: Column = otherColumns.reduce(_+_).divide(60.0).as("other")  
                                   

    df.select( workingStatusProjection, sexProjection, ageProjection
             , primaryNeeds, work, other
             ).where(df("telfs") <= 4) // Discard people who are not in labor force

  }

 
 
  /** @return the average daily time (in hours) spent in primary needs, working or leisure, grouped by the different
    *         ages of life (young, active or elder), sex and working status.
    * @param summed DataFrame returned by `timeUsageSumByClass`
    *
    * The resulting DataFrame should have the following columns:
    * - working: the "working" column of the `summed` DataFrame,
    * - sex: the "sex" column of the `summed` DataFrame,
    * - age: the "age" column of the `summed` DataFrame,
    * - primaryNeeds: the average value of the "primaryNeeds" columns of all the people that have the same working
    *   status, sex and age, rounded with a scale of 1 (using the `round` function),
    * - work: the average value of the "work" columns of all the people that have the same working status, sex
    *   and age, rounded with a scale of 1 (using the `round` function),
    * - other: the average value of the "other" columns all the people that have the same working status, sex and
    *   age, rounded with a scale of 1 (using the `round` function).
    *
    * Finally, the resulting DataFrame should be sorted by working status, sex and age.
    */
  def timeUsageGrouped(summed: DataFrame): DataFrame = {
    // { YOUR CODE HERE }
    val df = summed.select($"workingStatusProjection".as("working"),
                           $"sexProjection".as("sex"),
                           $"ageProjection".as("age"),
                           $"primaryNeeds",
                           $"work",
                           $"other"
                          )
    
    val ds: RelationalGroupedDataset = df.groupBy("working","sex","age")
    // { YOUR CODE HERE }
    ds.agg(round(avg("primaryNeeds"),1).as("primaryNeeds"),
           round(avg("work"),1).as("work"),
           round(avg("other"),1).as("other")
          )
      .orderBy("working","sex","age")

  }
  
   ///def timeUsageGrouped(summed: DataFrame): DataFrame =
    ///summed.groupBy("working", "sex", "age")
     /// .agg(round(avg("primaryNeeds"), 1).as("primaryNeeds"),
     //     round(avg("work"), 1).as("work"),
      //    round(avg("other"), 1).as("other"))
     // .orderBy("working", "sex", "age")

     /**
       * @return Same as `timeUsageGrouped`, but using a plain SQL query instead
       * @param summed DataFrame returned by `timeUsageSumByClass`
       */
     def timeUsageGroupedSql(summed: DataFrame): DataFrame = {
       val viewName = s"summed"
       summed.createOrReplaceTempView(viewName)
       spark.sql(timeUsageGroupedSqlQuery(viewName))
     }
   
   
     /** @return SQL query equivalent to the transformation implemented in `timeUsageGrouped`
       * @param viewName Name of the SQL view to use
       */
     def timeUsageGroupedSqlQuery(viewName: String): String = 
       
       s"SELECT workingStatusProjection as working,sexProjection as sex,ageProjection as age,"+
       s"round(avg(primaryNeeds), 1),"+
       s"round(avg(work), 1),"+
       s"round(avg(other), 1)"+
       s"FROM $viewName "+
       s"GROUP BY working,sex,age "+
       s"ORDER BY working,sex,age"
   
   
   
     /**
       * @return A `Dataset[TimeUsageRow]` from the "untyped" `DataFrame`
       * @param timeUsageSummaryDf `DataFrame` returned by the `timeUsageSummary` method
       *
       * Hint: you should use the `getAs` method of `Row` to look up columns and
       * cast them at the same time.
       */
     def timeUsageSummaryTyped(timeUsageSummaryDf: DataFrame): Dataset[TimeUsageRow] = {
       
       timeUsageSummaryDf.map(row=> {TimeUsageRow(row.getAs[String]("workingStatusProjection"),
                                                  row.getAs[String]("sexProjection"),
                                                  row.getAs[String]("ageProjection"),
                                                  row.getAs[Double]("primaryNeeds"),
                                                  row.getAs[Double]("work"),
                                                  row.getAs[Double]("other")
                                                  )
                                     })
       }
   
       /**
       * @return Same as `timeUsageGrouped`, but using the typed API when possible
       * @param summed Dataset returned by the `timeUsageSummaryTyped` method
       *
       * Note that, though they have the same type (`Dataset[TimeUsageRow]`), the input
       * dataset contains one element per respondent, whereas the resulting dataset
       * contains one element per group (whose time spent on each activity kind has
       * been aggregated).
       *
       * Hint: you should use the `groupByKey` and `typed.avg` methods.
       */
     def timeUsageGroupedTyped(summed: Dataset[TimeUsageRow]): Dataset[TimeUsageRow] = {
       import org.apache.spark.sql.expressions.scalalang.typed
       import spark.implicits._
       
       summed.groupByKey(row=>(row.working,row.sex,row.age))
             .agg(round(typed.avg[TimeUsageRow](_.primaryNeeds),1).as(Encoders.DOUBLE),
                 round(typed.avg[TimeUsageRow](_.work),1).as(Encoders.DOUBLE),
                 round(typed.avg[TimeUsageRow](_.other),1).as(Encoders.DOUBLE),
                 )
             .map{case((working,sex,age),primaryNeeds,work,other) => 
                  TimeUsageRow(working,sex,age,primaryNeeds,work,other)
                 }
             .orderBy("working","sex","age")
   
     }
   
   }






