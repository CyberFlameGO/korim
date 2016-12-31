package com.soywiz.korim.bitmap

import com.soywiz.korim.color.RGBA

class Bitmap32(
	width: Int,
	height: Int,
	val data: IntArray = IntArray(width * height)
) : Bitmap(width, height) {
	constructor(width: Int, height: Int, generator: (x: Int, y: Int) -> Int) : this(width, height, IntArray(width * height) { generator(it % width, it / width) })

	operator fun set(x: Int, y: Int, color: Int) = apply { data[index(x, y)] = color }
	operator fun get(x: Int, y: Int): Int = data[index(x, y)]
	override fun get32(x: Int, y: Int): Int = get(x, y)

	fun setRow(y: Int, row: IntArray) {
		System.arraycopy(row, 0, data, index(0, y), width)
	}

	fun _draw(other: Bitmap32, dx: Int, dy: Int, sleft: Int, stop: Int, sright: Int, sbottom: Int, mix: Boolean) {
		val src = other
		val dst = this
		val width = sright - sleft
		val height = sbottom - stop
		for (y in 0 until height) {
			val dstOffset = dst.index(dx, dy + y)
			val srcOffset = src.index(sleft, stop + y)
			if (mix) {
				for (x in 0 until width) dst.data[dstOffset + x] = mix(dst.data[dstOffset + x], src.data[srcOffset + x])
			} else {
				for (x in 0 until width) dst.data[dstOffset + x] = src.data[srcOffset + x]
			}
		}
	}

	fun _drawPut(mix: Boolean, other: Bitmap32, _dx: Int = 0, _dy: Int = 0) {
		var dx = _dx
		var dy = _dy
		var sleft = 0
		var stop = 0
		val sright = other.width
		val sbottom = other.height
		if (dx < 0) {
			sleft = -dx
			//sright += dx
			dx = 0
		}
		if (dy < 0) {
			stop = -dy
			//sbottom += dy
			dy = 0
		}

		_draw(other, dx, dy, sleft, stop, sright, sbottom, mix)
	}

	fun put(other: Bitmap32, dx: Int = 0, dy: Int = 0) = _drawPut(false, other, dx, dy)
	fun draw(other: Bitmap32, dx: Int = 0, dy: Int = 0) = _drawPut(true, other, dx, dy)

	fun sliceWithBounds(left: Int, top: Int, right: Int, bottom: Int): Bitmap32 = sliceWithSize(left, top, right - left, bottom - top)

	fun sliceWithSize(x: Int, y: Int, width: Int, height: Int): Bitmap32 {
		val out = Bitmap32(width, height)
		for (yy in 0 until height) for (xx in 0 until width) {
			out[xx, y] = this[x + xx, y + yy]
		}
		return out
	}

	fun mix(dst: Int, src: Int): Int {
		val a = RGBA.getA(src)
		return when (a) {
			0x000 -> dst
			0xFF -> src
			else -> {
				RGBA.packRGB_A(
					RGBA.blend(dst, src, a * 256 / 255),
					RGBA.clampFF(RGBA.getA(dst) + RGBA.getA(src))
				)
			}
		}
	}

	inline fun setEach(callback: (x: Int, y: Int) -> Int) {
		for (y in 0 until height) {
			for (x in 0 until width) {
				this[x, y] = callback(x, y)
			}
		}
	}

	fun writeChannel(destination: BitmapChannel, input: Bitmap32, source: BitmapChannel) {
		val sourceShift = source.index * 8
		val destShift = destination.index * 8
		val destClear = (0xFF shl destShift).inv()
		for (n in 0 until area) {
			val c = (input.data[n] ushr sourceShift) and 0xFF
			this.data[n] = (this.data[n] and destClear) or (c shl destShift)
		}
	}

	fun writeChannel(destination: BitmapChannel, input: Bitmap8) {
		val destShift = destination.index * 8
		val destClear = (0xFF shl destShift).inv()
		for (n in 0 until area) {
			val c = input.data[n].toInt() and 0xFF
			this.data[n] = (this.data[n] and destClear) or (c shl destShift)
		}
	}

	companion object {
		fun createWithAlpha(color: Bitmap32, alpha: Bitmap32): Bitmap32 {
			val out = Bitmap32(color.width, color.height)
			for (n in 0 until out.area) {
				out.data[n] = (color.data[n] and 0x00FFFFFF) or (alpha.data[n] shl 24)
			}
			return out
		}
	}

	fun invert() = xor(0x00FFFFFF)

	fun xor(value: Int) {
		for (n in 0 until area) data[n] = data[n] xor value
	}

	override fun toString(): String = "Bitmap32($width, $height)"

	fun flipY() {
		for (y in 0 until height / 2) {
			swapRows(y, height - y - 1)
		}
	}

	fun swapRows(y0: Int, y1: Int) {
		val s0 = index(0, y0)
		val s1 = index(0, y1)

		for (x in 0 until width) {
			val v0 = this.data[s0 + x]
			val v1 = this.data[s1 + x]
			this.data[s1 + x] = v0
			this.data[s0 + x] = v1
		}
	}
}