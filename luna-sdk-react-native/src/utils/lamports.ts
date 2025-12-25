/**
 * Lamport conversion utilities
 * 1 SOL = 1,000,000,000 lamports
 */

const LAMPORTS_PER_SOL = 1_000_000_000;

/**
 * Convert SOL to lamports
 */
export function solToLamports(sol: number): number {
  return Math.round(sol * LAMPORTS_PER_SOL);
}

/**
 * Convert lamports to SOL
 */
export function lamportsToSol(lamports: number): number {
  return lamports / LAMPORTS_PER_SOL;
}

/**
 * Format lamports as SOL with specified decimal places
 */
export function formatSol(lamports: number, decimals: number = 9): string {
  const sol = lamportsToSol(lamports);
  return sol.toFixed(decimals);
}

/**
 * Format SOL with commas and currency symbol
 */
export function formatSolWithSymbol(lamports: number, decimals: number = 4): string {
  const sol = lamportsToSol(lamports);
  return `◎ ${sol.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}`;
}

/**
 * Parse a SOL string to lamports
 */
export function parseSolToLamports(solString: string): number {
  const cleaned = solString.replace(/[◎,\s]/g, '');
  const sol = parseFloat(cleaned);
  if (isNaN(sol)) {
    throw new Error(`Invalid SOL string: ${solString}`);
  }
  return solToLamports(sol);
}

/**
 * Check if lamports represents a whole SOL amount
 */
export function isWholeSol(lamports: number): boolean {
  return lamports % LAMPORTS_PER_SOL === 0;
}

export { LAMPORTS_PER_SOL };
export default { 
  solToLamports, 
  lamportsToSol, 
  formatSol, 
  formatSolWithSymbol, 
  parseSolToLamports,
  isWholeSol,
  LAMPORTS_PER_SOL 
};
