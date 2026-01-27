/**
 * Base58 encoding/decoding utilities
 */

const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const ALPHABET_MAP = new Map<string, number>();
for (let i = 0; i < ALPHABET.length; i++) {
  ALPHABET_MAP.set(ALPHABET[i], i);
}

/**
 * Encode a Uint8Array to base58 string
 */
export function encode(bytes: Uint8Array): string {
  if (bytes.length === 0) return '';

  // Count leading zeros
  let zeros = 0;
  while (zeros < bytes.length && bytes[zeros] === 0) {
    zeros++;
  }

  // Allocate enough space in big-endian base58 representation
  const size = ((bytes.length - zeros) * 138 / 100 + 1) >>> 0;
  const b58 = new Uint8Array(size);

  // Process the bytes
  let length = 0;
  for (let i = zeros; i < bytes.length; i++) {
    let carry = bytes[i];
    let j = 0;
    for (let k = size - 1; k >= 0 && (carry !== 0 || j < length); k--, j++) {
      carry += 256 * b58[k];
      b58[k] = carry % 58;
      carry = (carry / 58) >>> 0;
    }
    length = j;
  }

  // Skip leading zeros in base58 result
  let start = size - length;
  while (start < size && b58[start] === 0) {
    start++;
  }

  // Translate the result into a string
  let result = ALPHABET[0].repeat(zeros);
  for (; start < size; start++) {
    result += ALPHABET[b58[start]];
  }

  return result;
}

/**
 * Decode a base58 string to Uint8Array
 */
export function decode(str: string): Uint8Array {
  if (str.length === 0) return new Uint8Array(0);

  // Count leading '1's
  let zeros = 0;
  while (zeros < str.length && str[zeros] === '1') {
    zeros++;
  }

  // Allocate enough space
  const size = ((str.length - zeros) * 733 / 1000 + 1) >>> 0;
  const bytes = new Uint8Array(size);

  // Process the characters
  let length = 0;
  for (let i = zeros; i < str.length; i++) {
    const value = ALPHABET_MAP.get(str[i]);
    if (value === undefined) {
      throw new Error(`Invalid base58 character: ${str[i]}`);
    }

    let carry = value;
    let j = 0;
    for (let k = size - 1; k >= 0 && (carry !== 0 || j < length); k--, j++) {
      carry += 58 * bytes[k];
      bytes[k] = carry % 256;
      carry = (carry / 256) >>> 0;
    }
    length = j;
  }

  // Skip leading zeros in result
  let start = size - length;
  while (start < size && bytes[start] === 0) {
    start++;
  }

  // Create result with leading zeros
  const result = new Uint8Array(zeros + (size - start));
  result.fill(0, 0, zeros);
  result.set(bytes.subarray(start), zeros);

  return result;
}

/**
 * Check if a string is valid base58
 */
export function isValid(str: string): boolean {
  for (const char of str) {
    if (!ALPHABET_MAP.has(char)) {
      return false;
    }
  }
  return true;
}

export default { encode, decode, isValid };
