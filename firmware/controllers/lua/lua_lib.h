/**
 * file lua_lib.h
 * if you like any of those you would have to copy paste into your script manually - those
 * are NOT part of the default anything automatically
 * please remove slash from the end of each line
 */

#define ARRAY_EQUALS "function equals(data1, data2) \
 \
  local index = 1 \
  if data1 == nil then \
     return -666 \
  end \
  while data1[index] ~= nil do \
	if math.floor(data1[index]) ~= math.floor(data2[index]) then \
       return -1 - index \
    end \
	index = index + 1 \
  end \
	if nil ~= data2[index] then \
       return -1 - index \
    end \
  return 0 \
end \
	"

#define LUA_POW " \
function pow(x, power) \
	local result = x \
	for i = 2, power, 1 \
	do \
		result = result * x \
	end \
	return result \
end \
"

#define PRINT_ARRAY "hexstr = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, \"A\", \"B\", \"C\", \"D\", \"E\", \"F\" } \
\
function toHexString(num)  \
	if num == 0 then \
		return '0' \
	end \
 \
	local result = \"\"   \
	while num > 0 do   \
		local n = num % 16  \
		result = hexstr[n + 1] ..result   \
		num = math.floor(num / 16)   \
	end  \
	return result  \
end   \
\
function arrayToString(arr)  \
	local str = \"\"   \
	local index = 1   \
	while arr[index] ~= nil do  \
		str = str..\" \"..toHexString(math.floor(arr[index]))  \
		index = index + 1\
	end  \
	return str  \
end  \
 \
\
"

// LSB (Least Significant Byte comes first) "Intel"
// see also getTwoBytesLsb
#define TWO_BYTES_LSB "function getTwoBytesLSB(data, offset, factor)\
		return (data[offset + 2] * 256 + data[offset + 1]) * factor \n\
	end\n\
\
"

// Little-endian System, "Intel"
#define SET_TWO_BYTES_LSB "	function setTwoBytesLsb(data, offset, value) \
		value = math.floor(value)\
		data[offset + 2] = value >> 8\
		data[offset + 1] = value & 0xff\
	end \
"

// MOTOROLA order, MSB (Most Significant Byte/Big Endian) comes first.
// see also getTwoBytesMsb
#define TWO_BYTES_MSB "function getTwoBytesMSB(data, offset, factor)        \
		return (data[offset + 1] * 256 + data[offset + 2]) * factor   \
	end \
"

// see also CanTxMessage#setShortValueMsb
#define SET_TWO_BYTES_MSB "	function setTwoBytesMsb(data, offset, value) \
		value = math.floor(value) \
		data[offset + 1] = value >> 8 \
		data[offset + 2] = value & 0xff \
	end\
"

// one day we shall get Preprocessor macros with C++11 raw string literals
// https://gcc.gnu.org/bugzilla/show_bug.cgi?id=55971
// for when you want "I want bitWidth number of bits starting at bitIndex in data array
#define GET_BIT_RANGE_LSB " \
function getBitRange(data, bitIndex, bitWidth) \n\
	local byteIndex = bitIndex >> 3 \n\
	local shift = bitIndex - byteIndex * 8 \n\
	local value = data[1 + byteIndex] \n\
	if (shift + bitWidth > 8) then \n\
		value = value + data[2 + byteIndex] * 256 \
	end \n\
	local mask = (1 << bitWidth) - 1 \n\
	return (value >> shift) & mask \n\
end \n\
"

// Motorola big-endian
#define GET_BIT_RANGE_MSB " \
function getBitRangeMsb(data, bitIndex, bitWidth) \n\
	local byteIndex = bitIndex >> 3 \n\
	local shift = bitIndex - byteIndex * 8 \n\
	local value = data[1 + byteIndex] \n\
	if (shift + bitWidth > 8) then \n\
		value = value + data[0 + byteIndex] * 256 \
	end \n\
	local mask = (1 << bitWidth) - 1 \n\
	return (value >> shift) & mask \n\
end \n\
"

#define SET_BIT_RANGE_LSB " \
function setBitRange(data, totalBitIndex, bitWidth, value) \
	local byteIndex = totalBitIndex >> 3 \
	local bitInByteIndex = totalBitIndex - byteIndex * 8 \
	if (bitInByteIndex + bitWidth > 8) then \
		local bitsToHandleNow = 8 - bitInByteIndex \
		setBitRange(data, totalBitIndex + bitsToHandleNow, bitWidth - bitsToHandleNow, value >> bitsToHandleNow) \
		bitWidth = bitsToHandleNow \
	end \
	local mask = (1 << bitWidth) - 1 \
	data[1 + byteIndex] = data[1 + byteIndex] & (~(mask << bitInByteIndex)) \
	local maskedValue = value & mask \
	local shiftedValue = maskedValue << bitInByteIndex \
	data[1 + byteIndex] = data[1 + byteIndex] | shiftedValue \
end \n\
"

#define SET_BIT_RANGE_MSB " \
function setBitRangeMsb(data, totalBitIndex, bitWidth, value) \
	local byteIndex = totalBitIndex >> 3 \
	local bitInByteIndex = totalBitIndex - byteIndex * 8 \
	if (bitInByteIndex + bitWidth > 8) then \
		local bitsToHandleNow = 8 - bitInByteIndex \
		setBitRangeMsb(data, (byteIndex - 1) * 8, bitWidth - bitsToHandleNow, value >> bitsToHandleNow) \
		bitWidth = bitsToHandleNow \
	end \
	local mask = (1 << bitWidth) - 1 \
	data[1 + byteIndex] = data[1 + byteIndex] & (~(mask << bitInByteIndex)) \
	local maskedValue = value & mask \
	local shiftedValue = maskedValue << bitInByteIndex \
	data[1 + byteIndex] = data[1 + byteIndex] | shiftedValue \
end \n\
"

#define HYUNDAI_SUM_NIBBLES "\
function hyundaiSumNibbles(data, seed) \n\
  local sum = seed \n\
  for i = 1, 7, 1 \n\
  do \n\
    local b = data[i] \n\
    sum =  sum +  (b % 16) + math.floor(b / 16) \
  end \
  return (16 - sum) % 16 \
end\
"

// XOR of the array, skipping target index
#define VAG_CHECKSUM " \
function xorChecksum(data, targetIndex) \
	local index = 1 \
	local result = 0 \
	while data[index] ~= nil do \
		if index ~= targetIndex then \
			result = result ~ data[index] \
		end \
		index = index + 1 \
	end \
	data[targetIndex] = result \
	return result \
end \
"
