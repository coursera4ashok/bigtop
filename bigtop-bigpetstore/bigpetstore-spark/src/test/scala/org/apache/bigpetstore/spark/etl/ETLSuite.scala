/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.bigtop.bigpetstore.spark.etl

import Array._

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

import org.apache.spark.{SparkContext, SparkConf}

import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.apache.bigtop.bigpetstore.spark.datamodel._

// hack for running tests with Gradle
@RunWith(classOf[JUnitRunner])
class IOUtilsSuite extends FunSuite with BeforeAndAfterAll {

  var sc: Option[SparkContext] = None
  var rawRecords: Option[Array[(Store, Location, Customer, Location, TransactionProduct)]] = None
  var transactions: Option[Array[Transaction]] = None

  val stores = Array(Store(5L, "11553"), Store(1L, "98110"), Store(6L, "66067"))
  val locations =
    Array(
      Location("11553", "Uniondale", "NY"),
      Location("98110", "Bainbridge Islan", "WA"),
      Location("66067", "Ottawa", "KS"),
      Location("20152", "Chantilly", "VA"))
  val customers = Array(Customer(999L, "Cesareo", "Lamplough", "20152"))
  val products =
    Array(
      Product(1L, "dry dog food", Map("category" -> "dry dog food", "brand" -> "Happy Pup", "flavor" -> "Fish & Potato", "size" -> "30.0", "per_unit_cost" -> "2.67")),
      Product(0L, "poop bags", Map("category" -> "poop bags", "brand" -> "Dog Days", "color" -> "Blue", "size" -> "60.0", "per_unit_cost" -> "0.21")),
      Product(2L, "dry cat food", Map("category" -> "dry cat food", "brand" -> "Feisty Feline", "flavor" -> "Chicken & Rice", "size" -> "14.0", "per_unit_cost" -> "2.14")))

  val rawLines = Array(
    "5,11553,Uniondale,NY,999,Cesareo,Lamplough,20152,Chantilly,VA,32,Tue Nov 03 01:08:11 EST 2015,category=dry dog food;brand=Happy Pup;flavor=Fish & Potato;size=30.0;per_unit_cost=2.67;",
    "1,98110,Bainbridge Islan,WA,999,Cesareo,Lamplough,20152,Chantilly,VA,31,Mon Nov 02 17:51:37 EST 2015,category=poop bags;brand=Dog Days;color=Blue;size=60.0;per_unit_cost=0.21;",
    "6,66067,Ottawa,KS,999,Cesareo,Lamplough,20152,Chantilly,VA,30,Mon Oct 12 04:29:46 EDT 2015,category=dry cat food;brand=Feisty Feline;flavor=Chicken & Rice;size=14.0;per_unit_cost=2.14;")

  override def beforeAll() {
    val conf = new SparkConf().setAppName("BPS Data Generator Test Suite").setMaster("local[2]")
    sc = Some(new SparkContext(conf))

    val cal1 = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), Locale.US)
    val cal2 = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), Locale.US)
    val cal3 = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), Locale.US)

    // Calendar seems to interpet months as 0-11
    // ms are not in output we parse.
    // have to set ms to 0, otherwise calendar will
    // use current system's ms.
    cal1.set(2015, 10, 3, 1, 8, 11)
    cal1.set(Calendar.MILLISECOND, 0)

    cal2.set(2015, 10, 2, 17, 51, 37)
    cal2.set(Calendar.MILLISECOND, 0)

    cal3.set(2015, 9, 12, 4, 29, 46)
    cal3.set(Calendar.MILLISECOND, 0)

    rawRecords = Some(Array(
      (stores(0), locations(0), customers(0), locations(3),
        TransactionProduct(999L, 32L, 5L, cal1, "category=dry dog food;brand=Happy Pup;flavor=Fish & Potato;size=30.0;per_unit_cost=2.67;")),

      (stores(1), locations(1), customers(0), locations(3),
      TransactionProduct(999L, 31L, 1L, cal2, "category=poop bags;brand=Dog Days;color=Blue;size=60.0;per_unit_cost=0.21;")),

      (stores(2), locations(2), customers(0), locations(3),
      TransactionProduct(999L, 30L, 6L, cal3, "category=dry cat food;brand=Feisty Feline;flavor=Chicken & Rice;size=14.0;per_unit_cost=2.14;"))))

    transactions = Some(Array(
      Transaction(999L, 31L, 1L, cal2, 0L),
      Transaction(999L, 30L, 6L, cal3, 2L),
      Transaction(999L, 32L, 5L, cal1, 1L)))
  }

  override def afterAll() {
    sc.get.stop()
  }

  test("Parse Raw Data") {
    val rawRDD = sc.get.parallelize(rawLines)
    val rdds = SparkETL.parseRawData(rawRDD)

    assert(rdds.collect().toSet === rawRecords.get.toSet)
  }

  test("Normalize Data") {
    val rawRDD = sc.get.parallelize(rawRecords.get)
    val rdds = SparkETL.normalizeData(rawRDD)

    val locationRDD = rdds._1
    val storeRDD = rdds._2
    val customerRDD = rdds._3
    val productRDD = rdds._4
    val transactionRDD = rdds._5

    assert(storeRDD.collect().toSet === stores.toSet)
    assert(locationRDD.collect().toSet === locations.toSet)
    assert(customerRDD.collect().toSet === customers.toSet)
    assert(productRDD.collect().toSet === products.toSet)
    assert(transactionRDD.collect().toSet === transactions.get.toSet)
  }
}
