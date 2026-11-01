package com.raulh.randomreminders.domain

import com.raulh.randomreminders.domain.model.ReminderRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomTimeGeneratorTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    private val rule = ReminderRule(
        start = LocalTime.of(8, 0),
        end = LocalTime.of(20, 0),
        shotsPerRun = 5,
        minSpacingMinutes = 60,
        intervalDays = 1,
    )

    private val activationDate: LocalDate = LocalDate.of(2026, 11, 1)

    @Test
    fun `gera a quantidade exata de disparos`() {
        val generator = RandomTimeGenerator(Random(42))
        val shots = generator.generateShots(rule, activationDate)
        assertEquals(5, shots.size)
    }

    @Test
    fun `todos os disparos ficam dentro da janela`() {
        val generator = RandomTimeGenerator(Random(42))
        val shots = generator.generateShots(rule, activationDate)

        shots.forEach { shot ->
            val time = LocalTime.from(shot)
            assertTrue("disparo antes da janela: $shot", !time.isBefore(rule.start))
            assertTrue("disparo depois da janela: $shot", !time.isAfter(rule.end))
        }
    }

    @Test
    fun `espacamento entre disparos respeita o minimo`() {
        val generator = RandomTimeGenerator(Random(42))
        val shots = generator.generateShots(rule, activationDate)

        shots.zipWithNext().forEach { (previous, current) ->
            val spacing = java.time.Duration.between(previous, current).toMinutes()
            assertTrue("espaçamento $spacing < 60 entre $previous e $current", spacing >= 60)
        }
    }

    @Test
    fun `disparos sao aleatorios entre geracoes independentes`() {
        val first = RandomTimeGenerator(Random(1)).generateShots(rule, activationDate)
        val second = RandomTimeGenerator(Random(99)).generateShots(rule, activationDate)
        // Instâncias diferentes (sementes diferentes) devem gerar conjuntos distintos
        // com probabilidade ~1 para 5 horários em uma janela de 720 minutos.
        assertTrue(first != second)
    }

    @Test
    fun `proxima ocorrencia respeita o intervalo de N dias`() {
        val everyThreeDays = rule.copy(intervalDays = 3)
        val generator = RandomTimeGenerator(Random(7))

        val from = ZonedDateTime.of(2026, 11, 1, 22, 0, 0, 0, zone) // fora da janela do dia 1
        val next = generator.nextOccurrence(everyThreeDays, activationDate, from)!!

        // Ativação no dia 01/11 + intervalo 3 => próximos ciclos: 04/11, 07/11, ...
        assertEquals(LocalDate.of(2026, 11, 4), next.toLocalDate())
        val time = LocalTime.from(next)
        assertTrue(!time.isBefore(everyThreeDays.start))
        assertTrue(!time.isAfter(everyThreeDays.end))
    }

    @Test
    fun `regra sem repeticao gera apenas os disparos da data informada`() {
        val oneTime = rule.copy(intervalDays = 0)
        val generator = RandomTimeGenerator(Random(7))

        val shots = generator.generateShots(oneTime, activationDate)
        val next = generator.nextOccurrence(
            oneTime,
            activationDate,
            ZonedDateTime.of(2026, 11, 1, 21, 0, 0, 0, zone),
        )

        assertEquals(5, shots.size)
        assertTrue(shots.all { it.toLocalDate() == activationDate })
        assertEquals(null, next)
    }

    @Test
    fun `proxima ocorrencia no mesmo dia considera apenas horarios futuros`() {
        val generator = RandomTimeGenerator(Random(7))
        val from = ZonedDateTime.of(2026, 11, 1, 15, 0, 0, 0, zone)

        val next = generator.nextOccurrence(rule, activationDate, from)!!

        assertTrue(next.isAfter(from))
        assertEquals(LocalDate.of(2026, 11, 1), next.toLocalDate())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `regra invalida e rejeitada`() {
        val invalid = rule.copy(start = LocalTime.of(20, 0), end = LocalTime.of(8, 0))
        RandomTimeGenerator(Random(42)).generateShots(invalid, activationDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `janela sem espaco para o espacamento minimo e rejeitada`() {
        // 14 disparos x 60 min = 780 min exigidos; janela tem 720 min -> regra reprova.
        val tight = rule.copy(shotsPerRun = 14, minSpacingMinutes = 60)
        RandomTimeGenerator(Random(42)).generateShots(tight, activationDate)
    }

    @Test
    fun `caso extremo de janela sem folga distribui uniformemente`() {
        // Janela de 120 min, 3 disparos, 60 min mínimos: folga = 0
        val exact = rule.copy(start = LocalTime.of(8, 0), end = LocalTime.of(10, 0), shotsPerRun = 3)
        val shots = RandomTimeGenerator(Random(42)).generateShots(exact, activationDate)

        assertEquals(3, shots.size)
        assertEquals(LocalTime.of(8, 0), LocalTime.from(shots.first()))
        assertEquals(LocalTime.of(9, 0), LocalTime.from(shots[1]))
        assertEquals(LocalTime.of(10, 0), LocalTime.from(shots[2]))
    }
}
