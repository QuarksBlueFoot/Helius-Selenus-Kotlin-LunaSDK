/**
 * Retry utilities with exponential backoff
 */

export interface RetryOptions {
  /** Maximum number of retry attempts */
  maxRetries?: number;
  /** Initial delay in milliseconds */
  initialDelay?: number;
  /** Maximum delay in milliseconds */
  maxDelay?: number;
  /** Exponential backoff factor */
  backoffFactor?: number;
  /** Jitter factor (0-1) to randomize delays */
  jitter?: number;
  /** Function to determine if error is retryable */
  isRetryable?: (error: unknown) => boolean;
  /** Callback on each retry */
  onRetry?: (error: unknown, attempt: number, delay: number) => void;
}

const DEFAULT_OPTIONS: Required<RetryOptions> = {
  maxRetries: 3,
  initialDelay: 1000,
  maxDelay: 30000,
  backoffFactor: 2,
  jitter: 0.1,
  isRetryable: () => true,
  onRetry: () => {},
};

/**
 * Sleep for specified milliseconds
 */
export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Calculate delay with exponential backoff and jitter
 */
export function calculateDelay(
  attempt: number,
  initialDelay: number,
  maxDelay: number,
  backoffFactor: number,
  jitter: number
): number {
  const exponentialDelay = initialDelay * Math.pow(backoffFactor, attempt);
  const cappedDelay = Math.min(exponentialDelay, maxDelay);
  const jitterAmount = cappedDelay * jitter * (Math.random() * 2 - 1);
  return Math.max(0, cappedDelay + jitterAmount);
}

/**
 * Retry a function with exponential backoff
 */
export async function retry<T>(
  fn: () => Promise<T>,
  options: RetryOptions = {}
): Promise<T> {
  const opts = { ...DEFAULT_OPTIONS, ...options };
  let lastError: unknown;

  for (let attempt = 0; attempt <= opts.maxRetries; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;

      if (attempt >= opts.maxRetries || !opts.isRetryable(error)) {
        throw error;
      }

      const delay = calculateDelay(
        attempt,
        opts.initialDelay,
        opts.maxDelay,
        opts.backoffFactor,
        opts.jitter
      );

      opts.onRetry(error, attempt + 1, delay);
      await sleep(delay);
    }
  }

  throw lastError;
}

/**
 * Retry with specific conditions for RPC errors
 */
export async function retryRpc<T>(
  fn: () => Promise<T>,
  options: Omit<RetryOptions, 'isRetryable'> = {}
): Promise<T> {
  return retry(fn, {
    ...options,
    isRetryable: (error: unknown) => {
      // Retry on network errors
      if (error instanceof TypeError && error.message.includes('fetch')) {
        return true;
      }
      // Retry on rate limiting
      if (error && typeof error === 'object' && 'status' in error) {
        const status = (error as { status: number }).status;
        return status === 429 || status >= 500;
      }
      // Retry on specific RPC errors
      if (error && typeof error === 'object' && 'code' in error) {
        const code = (error as { code: number }).code;
        // -32005 is node unhealthy, -32016 is server busy
        return code === -32005 || code === -32016;
      }
      return false;
    },
  });
}

/**
 * Create a debounced function
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;

  return (...args: Parameters<T>) => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    timeoutId = setTimeout(() => fn(...args), delay);
  };
}

/**
 * Create a throttled function
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle = false;

  return (...args: Parameters<T>) => {
    if (!inThrottle) {
      fn(...args);
      inThrottle = true;
      setTimeout(() => {
        inThrottle = false;
      }, limit);
    }
  };
}

export default { retry, retryRpc, sleep, calculateDelay, debounce, throttle };
