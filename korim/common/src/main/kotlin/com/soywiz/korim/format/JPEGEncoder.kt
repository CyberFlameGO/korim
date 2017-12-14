package com.soywiz.korim.format

import com.soywiz.klock.Klock
import com.soywiz.korio.stream.ByteArrayBuilderSmall
import kotlin.math.floor
import kotlin.math.round

/*
  Copyright (c) 2008, Adobe Systems Incorporated
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of Adobe Systems Incorporated nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
JPEG encoder ported to JavaScript and optimized by Andreas Ritter, www.bytestrom.eu, 11/2009
JPEG encoder ported to Kotlin by soywiz

Basic GUI blocking jpeg encoder
*/

// Based on: https://github.com/eugeneware/jpeg-js/blob/652bfced3ead53808285b1b5fa9c0b589d00bbf0/lib/encoder.js
class JPEGEncoder(private val quality: Int = 50) {
	private var YTable = IntArray(64)
	private var UVTable = IntArray(64)
	private var fdtbl_Y = FloatArray(64)
	private var fdtbl_UV = FloatArray(64)
	private var YDC_HT: Array<IntArray> = emptyArray()
	private var UVDC_HT: Array<IntArray> = emptyArray()
	private var YAC_HT: Array<IntArray> = emptyArray()
	private var UVAC_HT: Array<IntArray> = emptyArray()

	private fun fround(value: Float): Int = round(value).toInt()
	private fun ffloor(value: Float): Int = floor(value).toInt()

	private var bitcode = Array(65535) { IntArray(2) }
	private var category = IntArray(65535)
	private var outputfDCTQuant = IntArray(64)
	private var DU = IntArray(64) // @TODO: Check Int or Float?
	private var byteout = ByteArrayBuilderSmall()
	private var bytenew = 0
	private var bytepos = 7

	private var YDU = FloatArray(64)
	private var UDU = FloatArray(64)
	private var VDU = FloatArray(64)
	private var RGB_YUV_TABLE = IntArray(2048)
	private var currentQuality: Int = 0

	private var ZigZag = intArrayOf(
		0, 1, 5, 6, 14, 15, 27, 28,
		2, 4, 7, 13, 16, 26, 29, 42,
		3, 8, 12, 17, 25, 30, 41, 43,
		9, 11, 18, 24, 31, 40, 44, 53,
		10, 19, 23, 32, 39, 45, 52, 54,
		20, 22, 33, 38, 46, 51, 55, 60,
		21, 34, 37, 47, 50, 56, 59, 61,
		35, 36, 48, 49, 57, 58, 62, 63
	)

	private var std_dc_luminance_nrcodes = intArrayOf(0, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
	private var std_dc_luminance_values = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
	private var std_ac_luminance_nrcodes = intArrayOf(0, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d)
	private var std_ac_luminance_values = intArrayOf(
		0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
		0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
		0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
		0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
		0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
		0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
		0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
		0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
		0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
		0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
		0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
		0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
		0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
		0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
		0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
		0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
		0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
		0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
		0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
		0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
		0xf9, 0xfa
	)

	private var std_dc_chrominance_nrcodes = intArrayOf(0, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)
	private var std_dc_chrominance_values = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
	private var std_ac_chrominance_nrcodes = intArrayOf(0, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77)
	private var std_ac_chrominance_values = intArrayOf(
		0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
		0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
		0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
		0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
		0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
		0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
		0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
		0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
		0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
		0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
		0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
		0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
		0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
		0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
		0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
		0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
		0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
		0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
		0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
		0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
		0xf9, 0xfa
	)

	private fun initQuantTables(sf: Int) {
		val YQT = intArrayOf(
			16, 11, 10, 16, 24, 40, 51, 61,
			12, 12, 14, 19, 26, 58, 60, 55,
			14, 13, 16, 24, 40, 57, 69, 56,
			14, 17, 22, 29, 51, 87, 80, 62,
			18, 22, 37, 56, 68, 109, 103, 77,
			24, 35, 55, 64, 81, 104, 113, 92,
			49, 64, 78, 87, 103, 121, 120, 101,
			72, 92, 95, 98, 112, 100, 103, 99
		)

		for (i in 0 until 64) {
			var t = ffloor((YQT[i] * sf + 50).toFloat() / 100f)
			if (t < 1) {
				t = 1
			} else if (t > 255) {
				t = 255
			}
			YTable[ZigZag[i]] = t
		}

		val UVQT = intArrayOf(
			17, 18, 24, 47, 99, 99, 99, 99,
			18, 21, 26, 66, 99, 99, 99, 99,
			24, 26, 56, 99, 99, 99, 99, 99,
			47, 66, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99
		)

		for (j in 0 until 64) {
			var u = ffloor((UVQT[j] * sf + 50f) / 100f)
			if (u < 1) {
				u = 1
			} else if (u > 255) {
				u = 255
			}
			UVTable[ZigZag[j]] = u
		}

		val aasf = floatArrayOf(
			1.0f, 1.387039845f, 1.306562965f, 1.175875602f,
			1.0f, 0.785694958f, 0.541196100f, 0.275899379f
		)
		var k = 0
		for (row in 0 until 8) {
			for (col in 0 until 8) {
				fdtbl_Y[k] = (1.0f / (YTable[ZigZag[k]].toFloat() * aasf[row] * aasf[col] * 8.0f))
				fdtbl_UV[k] = (1.0f / (UVTable[ZigZag[k]].toFloat() * aasf[row] * aasf[col] * 8.0f))
				k++
			}
		}
	}

	private fun computeHuffmanTbl(nrcodes: IntArray, std_table: IntArray): Array<IntArray> {
		var codevalue = 0
		var pos_in_table = 0
		val HT = Array((std_table.max() ?: 0) + 1) { IntArray(2) }
		for (k in 1..16) {
			for (j in 1..nrcodes[k]) {
				HT[std_table[pos_in_table]] = IntArray(2)
				HT[std_table[pos_in_table]][0] = codevalue
				HT[std_table[pos_in_table]][1] = k
				pos_in_table++
				codevalue++
			}
			codevalue *= 2
		}
		return HT
	}

	private fun initHuffmanTbl() {
		YDC_HT = computeHuffmanTbl(std_dc_luminance_nrcodes, std_dc_luminance_values)
		UVDC_HT = computeHuffmanTbl(std_dc_chrominance_nrcodes, std_dc_chrominance_values)
		YAC_HT = computeHuffmanTbl(std_ac_luminance_nrcodes, std_ac_luminance_values)
		UVAC_HT = computeHuffmanTbl(std_ac_chrominance_nrcodes, std_ac_chrominance_values)
	}

	private fun initCategoryNumber() {
		var nrlower = 1
		var nrupper = 2
		for (cat in 1..15) {
			//Positive numbers
			for (nr in nrlower until nrupper) {
				category[32767 + nr] = cat
				bitcode[32767 + nr] = IntArray(2)
				bitcode[32767 + nr][1] = cat
				bitcode[32767 + nr][0] = nr
			}
			//Negative numbers
			for (nrneg in (-(nrupper - 1))..(-nrlower)) {
				category[32767 + nrneg] = cat
				bitcode[32767 + nrneg] = IntArray(2)
				bitcode[32767 + nrneg][1] = cat
				bitcode[32767 + nrneg][0] = nrupper - 1 + nrneg
			}
			nrlower = nrlower shl 1
			nrupper = nrupper shl 1
		}
	}

	private fun initRGBYUVTable() {
		for (i in 0 until 256) {
			RGB_YUV_TABLE[(i + 0)] = 19595 * i
			RGB_YUV_TABLE[(i + 256)] = 38470 * i
			RGB_YUV_TABLE[(i + 512)] = 7471 * i + 0x8000
			RGB_YUV_TABLE[(i + 768)] = -11059 * i
			RGB_YUV_TABLE[(i + 1024)] = -21709 * i
			RGB_YUV_TABLE[(i + 1280)] = 32768 * i + 0x807FFF
			RGB_YUV_TABLE[(i + 1536)] = -27439 * i
			RGB_YUV_TABLE[(i + 1792)] = -5329 * i
		}
	}

	// IO functions
	private fun writeBits(bs: IntArray) {
		val value = bs[0]
		var posval = bs[1] - 1
		while (posval >= 0) {
			if ((value and (1 shl posval)) != 0) {
				bytenew = bytenew or (1 shl bytepos)
			}
			posval--
			bytepos--
			if (bytepos < 0) {
				if (bytenew == 0xFF) {
					writeByte(0xFF)
					writeByte(0)
				} else {
					writeByte(bytenew)
				}
				bytepos = 7
				bytenew = 0
			}
		}
	}

	private fun writeByte(value: Int) {
		byteout.append(value.toByte())
	}

	private fun writeWord(value: Int) {
		writeByte((value ushr 8) and 0xFF)
		writeByte((value) and 0xFF)
	}

	// DCT & quantization core
	private fun fDCTQuant(data: FloatArray, fdtbl: FloatArray): IntArray {
		var d0: Float
		var d1: Float
		var d2: Float
		var d3: Float
		var d4: Float
		var d5: Float
		var d6: Float
		var d7: Float
		/* Pass 1: process rows. */
		var dataOff = 0
		val I8 = 8
		val I64 = 64
		for (i in 0 until I8) {
			d0 = data[dataOff + 0]
			d1 = data[dataOff + 1]
			d2 = data[dataOff + 2]
			d3 = data[dataOff + 3]
			d4 = data[dataOff + 4]
			d5 = data[dataOff + 5]
			d6 = data[dataOff + 6]
			d7 = data[dataOff + 7]

			val tmp0 = d0 + d7
			val tmp7 = d0 - d7
			val tmp1 = d1 + d6
			val tmp6 = d1 - d6
			val tmp2 = d2 + d5
			val tmp5 = d2 - d5
			val tmp3 = d3 + d4
			val tmp4 = d3 - d4

			/* Even part */
			var tmp10 = tmp0 + tmp3    /* phase 2 */
			val tmp13 = tmp0 - tmp3
			var tmp11 = tmp1 + tmp2
			var tmp12 = tmp1 - tmp2

			data[dataOff] = tmp10 + tmp11 /* phase 3 */
			data[dataOff + 4] = tmp10 - tmp11

			val z1 = (tmp12 + tmp13) * 0.707106781f /* c4 */
			data[dataOff + 2] = tmp13 + z1 /* phase 5 */
			data[dataOff + 6] = tmp13 - z1

			/* Odd part */
			tmp10 = tmp4 + tmp5 /* phase 2 */
			tmp11 = tmp5 + tmp6
			tmp12 = tmp6 + tmp7

			/* The rotator is modified from fig 4-8 to avoid extra negations. */
			val z5 = (tmp10 - tmp12) * 0.382683433f /* c6 */
			val z2 = 0.541196100f * tmp10 + z5 /* c2-c6 */
			val z4 = 1.306562965f * tmp12 + z5 /* c2+c6 */
			val z3 = tmp11 * 0.707106781f /* c4 */

			val z11 = tmp7 + z3    /* phase 5 */
			val z13 = tmp7 - z3

			data[dataOff + 5] = z13 + z2    /* phase 6 */
			data[dataOff + 3] = z13 - z2
			data[dataOff + 1] = z11 + z4
			data[dataOff + 7] = z11 - z4

			dataOff += 8 /* advance pointer to next row */
		}

		/* Pass 2: process columns. */
		dataOff = 0
		for (i in 0 until I8) {
			d0 = data[dataOff]
			d1 = data[dataOff + 8]
			d2 = data[dataOff + 16]
			d3 = data[dataOff + 24]
			d4 = data[dataOff + 32]
			d5 = data[dataOff + 40]
			d6 = data[dataOff + 48]
			d7 = data[dataOff + 56]

			val tmp0p2 = d0 + d7
			val tmp7p2 = d0 - d7
			val tmp1p2 = d1 + d6
			val tmp6p2 = d1 - d6
			val tmp2p2 = d2 + d5
			val tmp5p2 = d2 - d5
			val tmp3p2 = d3 + d4
			val tmp4p2 = d3 - d4

			/* Even part */
			var tmp10p2 = tmp0p2 + tmp3p2    /* phase 2 */
			val tmp13p2 = tmp0p2 - tmp3p2
			var tmp11p2 = tmp1p2 + tmp2p2
			var tmp12p2 = tmp1p2 - tmp2p2

			data[dataOff] = tmp10p2 + tmp11p2 /* phase 3 */
			data[dataOff + 32] = tmp10p2 - tmp11p2

			val z1p2 = (tmp12p2 + tmp13p2) * 0.707106781f /* c4 */
			data[dataOff + 16] = tmp13p2 + z1p2 /* phase 5 */
			data[dataOff + 48] = tmp13p2 - z1p2

			/* Odd part */
			tmp10p2 = tmp4p2 + tmp5p2 /* phase 2 */
			tmp11p2 = tmp5p2 + tmp6p2
			tmp12p2 = tmp6p2 + tmp7p2

			/* The rotator is modified from fig 4-8 to avoid extra negations. */
			val z5p2 = (tmp10p2 - tmp12p2) * 0.382683433f /* c6 */
			val z2p2 = 0.541196100f * tmp10p2 + z5p2 /* c2-c6 */
			val z4p2 = 1.306562965f * tmp12p2 + z5p2 /* c2+c6 */
			val z3p2 = tmp11p2 * 0.707106781f /* c4 */

			val z11p2 = tmp7p2 + z3p2    /* phase 5 */
			val z13p2 = tmp7p2 - z3p2

			data[dataOff + 40] = z13p2 + z2p2 /* phase 6 */
			data[dataOff + 24] = z13p2 - z2p2
			data[dataOff + 8] = z11p2 + z4p2
			data[dataOff + 56] = z11p2 - z4p2

			dataOff++ /* advance pointer to next column */
		}

		// Quantize/descale the coefficients
		var fDCTQuant: Float
		for (i in 0 until I64) {
			// Apply the quantization and scaling factor & Round to nearest integer
			fDCTQuant = data[i] * fdtbl[i]
			outputfDCTQuant[i] = if (fDCTQuant > 0.0f) ((fDCTQuant + 0.5f).toInt()) else ((fDCTQuant - 0.5f).toInt())
			//outputfDCTQuant[i] = fround(fDCTQuant);

		}
		return outputfDCTQuant
	}

	private fun writeAPP0() {
		writeWord(0xFFE0) // marker
		writeWord(16) // length
		writeByte(0x4A) // J
		writeByte(0x46) // F
		writeByte(0x49) // I
		writeByte(0x46) // F
		writeByte(0) // = "JFIF",'\0'
		writeByte(1) // versionhi
		writeByte(1) // versionlo
		writeByte(0) // xyunits
		writeWord(1) // xdensity
		writeWord(1) // ydensity
		writeByte(0) // thumbnwidth
		writeByte(0) // thumbnheight
	}

	private fun writeSOF0(width: Int, height: Int) {
		writeWord(0xFFC0) // marker
		writeWord(17)   // length, truecolor YUV JPG
		writeByte(8)    // precision
		writeWord(height)
		writeWord(width)
		writeByte(3)    // nrofcomponents
		writeByte(1)    // IdY
		writeByte(0x11) // HVY
		writeByte(0)    // QTY
		writeByte(2)    // IdU
		writeByte(0x11) // HVU
		writeByte(1)    // QTU
		writeByte(3)    // IdV
		writeByte(0x11) // HVV
		writeByte(1)    // QTV
	}

	private fun writeDQT() {
		writeWord(0xFFDB) // marker
		writeWord(132)       // length
		writeByte(0)
		for (i in 0 until 64) {
			writeByte(YTable[i])
		}
		writeByte(1)
		for (j in 0 until 64) {
			writeByte(UVTable[j])
		}
	}

	private fun writeDHT() {
		writeWord(0xFFC4) // marker
		writeWord(0x01A2) // length

		writeByte(0) // HTYDCinfo
		for (i in 0 until 16) {
			writeByte(std_dc_luminance_nrcodes[i + 1])
		}
		for (j in 0..11) {
			writeByte(std_dc_luminance_values[j])
		}

		writeByte(0x10) // HTYACinfo
		for (k in 0 until 16) {
			writeByte(std_ac_luminance_nrcodes[k + 1])
		}
		for (l in 0..161) {
			writeByte(std_ac_luminance_values[l])
		}

		writeByte(1) // HTUDCinfo
		for (m in 0 until 16) {
			writeByte(std_dc_chrominance_nrcodes[m + 1])
		}
		for (n in 0..11) {
			writeByte(std_dc_chrominance_values[n])
		}

		writeByte(0x11) // HTUACinfo
		for (o in 0 until 16) {
			writeByte(std_ac_chrominance_nrcodes[o + 1])
		}
		for (p in 0..161) {
			writeByte(std_ac_chrominance_values[p])
		}
	}

	private fun writeSOS() {
		writeWord(0xFFDA) // marker
		writeWord(12) // length
		writeByte(3) // nrofcomponents
		writeByte(1) // IdY
		writeByte(0) // HTY
		writeByte(2) // IdU
		writeByte(0x11) // HTU
		writeByte(3) // IdV
		writeByte(0x11) // HTV
		writeByte(0) // Ss
		writeByte(0x3f) // Se
		writeByte(0) // Bf
	}

	private fun processDU(CDU: FloatArray, fdtbl: FloatArray, DC: Int, HTDC: Array<IntArray>, HTAC: Array<IntArray>): Int {
		@Suppress("LocalVariableName", "NAME_SHADOWING")
		var DC = DC
		val EOB = HTAC[0x00]
		val M16zeroes = HTAC[0xF0]
		var pos: Int
		val I16 = 16
		val I63 = 63
		val I64 = 64
		val DU_DCT = fDCTQuant(CDU, fdtbl)
		//ZigZag reorder
		for (j in 0 until I64) {
			DU[ZigZag[j]] = DU_DCT[j]
		}
		val Diff = DU[0] - DC
		DC = DU[0]
		//Encode DC
		if (Diff == 0) {
			writeBits(HTDC[0]) // Diff might be 0
		} else {
			pos = 32767 + Diff
			writeBits(HTDC[category[pos]])
			writeBits(bitcode[pos])
		}
		//Encode ACs
		var end0pos = 63 // was const... which is crazy
		while ((end0pos > 0) && (DU[end0pos] == 0)) end0pos--
		//end0pos = first element in reverse order !=0
		if (end0pos == 0) {
			writeBits(EOB)
			return DC
		}
		var i = 1
		var lng: Int
		while (i <= end0pos) {
			val startpos = i
			while ((DU[i] == 0) && (i <= end0pos)) i++
			var nrzeroes = i - startpos
			if (nrzeroes >= I16) {
				lng = nrzeroes shr 4
				for (nrmarker in 1..lng)
					writeBits(M16zeroes)
				nrzeroes = nrzeroes and 0xF
			}
			pos = 32767 + DU[i]
			writeBits(HTAC[(nrzeroes shl 4) + category[pos]])
			writeBits(bitcode[pos])
			i++
		}
		if (end0pos != I63) {
			writeBits(EOB)
		}
		return DC
	}

	// image data object
	fun encode(image: ImageData, quality: Int? = null): ByteArray {
		var time_start = Klock.currentTimeMillis()

		if (quality != null) setQuality(quality)

		// Initialize bit writer
		byteout = ByteArrayBuilderSmall()
		bytenew = 0
		bytepos = 7

		// Add JPEG headers
		writeWord(0xFFD8) // SOI
		writeAPP0()
		writeDQT()
		writeSOF0(image.width, image.height)
		writeDHT()
		writeSOS()

		// Encode 8x8 macroblocks
		var DCY = 0
		var DCU = 0
		var DCV = 0

		bytenew = 0
		bytepos = 7

		val imageData = image.data
		val width = image.width
		val height = image.height

		val quadWidth = width * 4

		var x: Int
		var y = 0
		var r: Int
		var g: Int
		var b: Int
		var start: Int
		while (y < height) {
			x = 0
			while (x < quadWidth) {
				start = quadWidth * y + x

				for (pos in 0 until 64) {
					val row = pos shr 3// /8
					val col = (pos and 7) * 4 // %8
					var p = start + (row * quadWidth) + col

					if (y + row >= height) { // padding bottom
						p -= (quadWidth * (y + 1 + row - height))
					}

					if (x + col >= quadWidth) { // padding right
						p -= ((x + col) - quadWidth + 4)
					}

					r = imageData[p++].toInt() and 0xFF
					g = imageData[p++].toInt() and 0xFF
					b = imageData[p++].toInt() and 0xFF


					/* // calculate YUV values dynamically
					YDU[pos]=((( 0.29900)*r+( 0.58700)*g+( 0.11400)*b))-128; //-0x80
					UDU[pos]=(((-0.16874)*r+(-0.33126)*g+( 0.50000)*b));
					VDU[pos]=((( 0.50000)*r+(-0.41869)*g+(-0.08131)*b));
					*/

					// use lookup table (slightly faster)
					YDU[pos] = (((RGB_YUV_TABLE[(r + 0)] + RGB_YUV_TABLE[(g + 256)] + RGB_YUV_TABLE[(b + 512)]) shr 16) - 128).toFloat()
					UDU[pos] = (((RGB_YUV_TABLE[(r + 768)] + RGB_YUV_TABLE[(g + 1024)] + RGB_YUV_TABLE[(b + 1280)]) shr 16) - 128).toFloat()
					VDU[pos] = (((RGB_YUV_TABLE[(r + 1280)] + RGB_YUV_TABLE[(g + 1536)] + RGB_YUV_TABLE[(b + 1792)]) shr 16) - 128).toFloat()

				}

				DCY = processDU(YDU, fdtbl_Y, DCY, YDC_HT, YAC_HT)
				DCU = processDU(UDU, fdtbl_UV, DCU, UVDC_HT, UVAC_HT)
				DCV = processDU(VDU, fdtbl_UV, DCV, UVDC_HT, UVAC_HT)
				x += 32
			}
			y += 8
		}


		////////////////////////////////////////////////////////////////

		// Do the bit alignment of the EOI marker
		if (bytepos >= 0) {
			val fillbits = IntArray(2)
			fillbits[1] = bytepos + 1
			fillbits[0] = (1 shl (bytepos + 1)) - 1
			writeBits(fillbits)
		}

		writeWord(0xFFD9) //EOI

		return byteout.toByteArray()
	}

	private fun setQuality(quality: Int) {
		@Suppress("NAME_SHADOWING")
		var quality = quality
		if (quality <= 0) {
			quality = 1
		}
		if (quality > 100) {
			quality = 100
		}

		if (currentQuality == quality) return // don't recalc if unchanged

		var sf = 0
		if (quality < 50) {
			sf = (5000 / quality)
		} else {
			sf = (200 - quality * 2)
		}

		initQuantTables(sf)
		currentQuality = quality
		//console.log('Quality set to: '+quality +'%');
	}

	init {
		val time_start = Klock.currentTimeMillis()
		// Create tables
		initHuffmanTbl()
		initCategoryNumber()
		initRGBYUVTable()

		setQuality(quality)
		var duration = Klock.currentTimeMillis() - time_start
		//console.log('Initialization '+ duration + 'ms');
	}

	data class ImageData(val data: ByteArray, val width: Int, val height: Int)

	companion object {
		fun encode(imgData: ImageData, qu: Int = 50): ByteArray {
			return JPEGEncoder(qu).encode(imgData, qu)
		}

	}
}
