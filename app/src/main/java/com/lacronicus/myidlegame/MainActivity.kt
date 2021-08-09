package com.lacronicus.myidlegame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.google.accompanist.flowlayout.FlowRow
import com.lacronicus.changenotifier.ChangeNotifierViewModel
import com.lacronicus.changenotifier.withChangeNotifier
import com.lacronicus.myidlegame.ui.theme.MyIdleGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigDecimal.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyIdleGameTheme {
                Surface(color = MaterialTheme.colors.background) {
                    Game()
                }
            }
        }
    }
}

//todo: inject sharedprefs for state storage
//that would require hilt or some custom nonsense, and that's not the point of this.
//if you wanted to, you could inject sharedprefs or whatever, and save/restore it in init
class IdleViewModel : ChangeNotifierViewModel() {
    var score: BigDecimal = valueOf(10000)
    var maxScore: BigDecimal = score
    val ownedFactories: MutableMap<IdleFactory, Int> = mutableMapOf()
    var lastTickTime = 0L

    init {
        lastTickTime = System.currentTimeMillis()
        viewModelScope.launch {
            while (true) {
                tick()
                delay(16)
            }
        }
    }

    fun tick() {
        val timeSinceLastTick = (System.currentTimeMillis() - lastTickTime)
        lastTickTime = System.currentTimeMillis()
        ownedFactories.entries.forEach {
            addScore(timeSinceLastTick * it.key.getBaseRate() * it.value / 1000.0)
            notifyListeners()
        }
    }

    fun addScore(value: Double) {
        score += valueOf(value)
        if (score > maxScore) {
            maxScore = score
        }
        notifyListeners()
    }

    fun ownsAny(factory: IdleFactory) = ownedFactories.contains(factory)

    fun numberOwned(factory: IdleFactory) = ownedFactories[factory]

    fun canAfford(factory: IdleFactory) = score >= valueOf(factory.price())

    //show the factory if...
    fun shouldShow(factory: IdleFactory) = ownsAny(factory) // you own at least one
            || valueOf(factory.price()) < maxScore.multiply(valueOf(2)) // you're half way to affording one
            || factory == IdleFactory.Foo // it is the first factory available

    fun purchase(factory: IdleFactory) {
        score = score.subtract(valueOf(factory.price()))
        ownedFactories[factory] = (ownedFactories[factory] ?: 0) + 1
        notifyListeners()
    }

    fun reset() {
        score = ZERO
        maxScore = ZERO
        ownedFactories.clear()
        notifyListeners()
    }
}

enum class IdleFactory {
    Foo, // todo come up with better names
    Bar,
    Baz,
    Fab,
    Fizz,
    Buzz,
    FizzBuzz;

    fun getName(): String = when (this) {
        Foo -> "Foo"
        Bar -> "Bar"
        Baz -> "Baz"
        Fab -> "Fab"
        else -> name
    }

    fun getColor(): Color = when (this) {
        Foo -> Color(0xFFDB5141)
        Bar -> Color(0xFFD03764)
        Baz -> Color(0xFF8E32AA)
        Fab -> Color(0xFF613DB1)
        Fizz -> Color(0xFF4751AF)
        Buzz -> Color(0xFF5495EC)
        FizzBuzz -> Color(0xFF58A7EE)
    }

    fun getBaseRate(): Long = when (this) {
        Foo -> 1
        else -> Math.pow(7.0, this.ordinal.toDouble() - 1).toLong()
    }

    fun price(): Double = Math.pow(10.0, this.ordinal.toDouble())
}


@Composable
fun Game() {
    val vm: IdleViewModel = withChangeNotifier(listen = false) // if listen is true, then this will recompose every time notifyListeners() is called
    //if listen is false, you can call methods on it without recomposing

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Score()
        Button(onClick = { vm.addScore(1.0) }) {
            Text(text = "Click!")
        }
        IdleFactory.values().forEach {
            //note: im specifying :Boolean here, but you don't need to. it will infer it because vm.shouldShow(it) returns a boolean.
            //it's easy to see if you're in android studio, but not so much if you're reading this on github
            val showFactory: Boolean = withChangeNotifier { vm: IdleViewModel -> vm.shouldShow(it) }

            if (showFactory) {
                FactoryListItem(factory = it)
            }
        }
        Button(onClick = { vm.reset() }, colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)) {
            Text(text = "Reset!")
        }
    }
}

@Composable
fun Score() {
    val score: BigDecimal = withChangeNotifier { vm: IdleViewModel -> vm.score } // same here. kotlin will infer that this returns a BigDecimal, but github readers won't see that
    val maxScore: BigDecimal = withChangeNotifier { vm: IdleViewModel -> vm.maxScore }
    Text(text = "Score: ${score.toBigInteger()}")
    Text(text = "Max: ${maxScore.toBigInteger()}")
}

@Composable
fun FactoryListItem(factory: IdleFactory) {
    val vm: IdleViewModel = withChangeNotifier(listen = false)
    val owned: Int? = withChangeNotifier { it: IdleViewModel -> it.numberOwned(factory) } // note that this will only force a recompose if the result of numberOwned changes, not every time the vm changes
    val canAfford: Boolean = withChangeNotifier { it: IdleViewModel -> it.canAfford(factory) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { vm.purchase(factory) },
                enabled = canAfford,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = factory.getColor())) {
                Text(text = "Purchase a ${factory.getName()} for ${factory.price()}")
            }
        }
        if (owned != null && owned > 0)
            FlowRow(modifier = Modifier.fillMaxWidth(0.75f)) {
                (1..owned).forEach {
                    Box(modifier = Modifier.padding(1.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(factory.getColor())
                        )
                    }
                }
            }
    }
}