def balance(chars : String): Boolean = {

     def test_balance(charsLeft :List[Char], count : Int): Boolean ={
     if (count<0) return false
     
     if (charsLeft.isEmpty) {
        if(count==0) return true
        else return false

     }
     if (charsLeft.head == '(') return test_balance(charsLeft.tail, count+1)
        else if (charsLeft.head == ')') return test_balance(charsLeft.tail, count-1)
        else return test_balance(charsLeft.tail, count)



     }
   return test_balance(chars.toList, 0)


}

val chars= "()()(()"
balance(chars)


def countChange(money: Int, coins: List[Int]): Int = {
    def countChangeAux(moneyLeft : Int, coinsLeft : List[Int]) : Int = {

    if (moneyLeft == 0) return 1
    else if(moneyLeft < 0 || coinsLeft.isEmpty) return 0
    
    else
   
    countChangeAux(moneyLeft - (coinsLeft.head), coinsLeft) + countChangeAux (moneyLeft, coinsLeft.tail)

  }
  countChangeAux(money, coins)

}
val l =List(1,2)
val p=4
countChange(p,l)
val r =List(5,10,20,50,100,200,500)
val v=300
countChange(v,r)