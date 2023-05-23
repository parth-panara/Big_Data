package timeusage

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class TimeUsageSuite extends AnyFlatSpec with Matchers {

  import TimeUsage.{read,classifiedColumns,timeUsageSummary,timeUsageGrouped,timeUsageGroupedSql,timeUsageSummaryTyped,timeUsageGroupedTyped, spark}


  def timeUsageSummaryDataFrame = {
    val (columns, initDf) = read("timeusage/atussum.csv")
    val (primaryNeedsColumns, workColumns, otherColumns) = classifiedColumns(columns)
    timeUsageSummary(primaryNeedsColumns, workColumns, otherColumns, initDf)
  }

  val timeUsageGroupedDataFrame = timeUsageGrouped(timeUsageSummaryDataFrame)

  val timeUsageGroupedSqlDataFrame = timeUsageGroupedSql(timeUsageSummaryDataFrame)

  val TUS = timeUsageSummaryDataFrame
  println(" ------- timeUsageSummary ------------------------")
  TUS.show()
  
  val TUG = timeUsageGrouped(TUS)
  println(" ------- timeUsageGrouped ------------------------")
  TUG.show()

  val TUG_SQL = timeUsageGroupedSql(TUS)
  println(" ------- timeUsageGroupedSql ------------------------")
  TUG_SQL.show()

  val TUS_typed = timeUsageSummaryTyped(TUS);
  println(" ------- timeUsageSummaryTyped --------");
  TUS_typed.show()

  val TUG_typed = timeUsageGroupedTyped(TUS_typed);
  println(" ------- timeUsageGroupedTyped -------");
  TUG_typed.show()
  
  import spark.implicits._
  val TUG_pn = TUG.where($"working" === "working" && 
                         $"age" === "active" && 
                         $"sex" === "male")
                  .select("primaryNeeds")
  TUG_pn.show()

  val TUG_sql_pn = TUG_SQL.where($"workingStatusProjection" === "working" && 
                                 $"ageProjection" === "active" && 
                                 $"sexProjection" === "male")
                          .select("round(avg(primaryNeeds), 1)")
  TUG_sql_pn.show()
  
  "TimeUsageSuite" should " test and compare TimeUsageSummary, TimeUsageGrouped, and TimeUsageGroupedSql" in {
    TUG_pn.first.getDouble(0) shouldEqual TUG_sql_pn.first.getDouble(0)
  }

}
