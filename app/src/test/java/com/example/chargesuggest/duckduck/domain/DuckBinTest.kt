package com.example.chargesuggest.duckduck.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckBinTest {
    @Test fun 처음에는_기본_오리로만_가득_찬다() {
        val bin = DuckBin.starter()
        assertEquals(DuckConfig.BIN_CAPACITY, bin.size)
        assertTrue(bin.ducks.all { it.type == DuckType.BASIC })
        assertEquals(bin.size, bin.ducks.map { it.id }.toSet().size) // 번호는 겹치지 않는다
    }

    @Test fun 가득_찬_통에_받으면_가장_오래된_오리가_나간다() {
        val bin = DuckBin.starter()
        val oldest = bin.ducks.first()
        val (next, evicted) = bin.add(DuckType.RAINCOAT)
        assertEquals(oldest, evicted)
        assertEquals(bin.size, next.size)
        assertEquals(DuckType.RAINCOAT, next.ducks.last().type)
        assertTrue(next.ducks.none { it.id == oldest.id })
    }

    @Test fun 자리가_있으면_아무도_나가지_않는다() {
        val (sent, _) = DuckBin.starter().sendOne(DuckType.BASIC)
        val (next, evicted) = sent.add(DuckType.SNOWMAN)
        assertNull(evicted)
        assertEquals(DuckConfig.BIN_CAPACITY, next.size)
    }

    @Test fun 보내면_같은_종류_중_가장_오래된_오리가_빠진다() {
        var bin = DuckBin.starter()
        bin = bin.add(DuckType.SUNGLASSES).first        // 가장 오래된 기본 오리가 나가고 선글라스 오리가 들어옴
        val (after, out) = bin.sendOne(DuckType.SUNGLASSES)
        assertEquals(DuckType.SUNGLASSES, out!!.type)
        assertEquals(bin.size - 1, after.size)
    }

    @Test fun 같은_종류가_없으면_가장_오래된_오리가_빠진다() {
        val bin = DuckBin.starter()
        val (after, out) = bin.sendOne(DuckType.RAINBOW)
        assertEquals(bin.ducks.first(), out)
        assertEquals(bin.size - 1, after.size)
    }

    @Test fun 빈_통에서_보내도_안전하다() {
        val (same, out) = DuckBin(emptyList()).sendOne(DuckType.BASIC)
        assertNull(out)
        assertEquals(0, same.size)
        assertNotNull(same)
    }

    @Test fun 새_오리_번호는_계속_늘어난다() {
        var bin = DuckBin.starter()
        val ids = mutableSetOf<Int>()
        repeat(40) { bin = bin.add(DuckType.BASIC).first; ids += bin.ducks.last().id }
        assertEquals(40, ids.size)
    }

    @Test fun 받은_오리는_받은_오리_표시와_도감_번호를_갖고_확인하면_표시가_사라진다() {
        val (bin, _) = DuckBin.starter().add(DuckType.RAINCOAT, fresh = true, entryId = 7, gift = true)
        assertTrue(bin.ducks.last().fresh)
        assertTrue(bin.ducks.last().gift)
        assertEquals(7, bin.ducks.last().entryId)
        val opened = bin.markOpened(7)
        assertTrue(opened.ducks.none { it.fresh })
        assertEquals(bin, bin.markOpened(99)) // 없는 번호는 그대로
    }
}
