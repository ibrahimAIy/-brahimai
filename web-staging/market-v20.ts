export type Candle = { time: number; open: number; high: number; low: number; close: number; volume: number };

export type MarketScoreInput = {
  price: number;
  ema20: number | null;
  ema50: number | null;
  sma200: number | null;
  rsi14: number | null;
  macdHistogram: number | null;
  volumeRatio: number | null;
  atr14: number | null;
  support: number | null;
  resistance: number | null;
  eventUncertainty?: boolean;
};

function finite(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

export function smaSeries(values: number[], period: number) {
  const out: Array<number | null> = new Array(values.length).fill(null);
  let sum = 0;
  for (let i = 0; i < values.length; i += 1) {
    sum += values[i];
    if (i >= period) sum -= values[i - period];
    if (i >= period - 1) out[i] = sum / period;
  }
  return out;
}

export function emaSeries(values: number[], period: number) {
  if (!values.length) return [];
  const k = 2 / (period + 1);
  let prev = values[0];
  return values.map((value, i) => {
    prev = i === 0 ? value : value * k + prev * (1 - k);
    return prev;
  });
}

export function bollinger(values: number[], period = 20, deviation = 2) {
  const middle = smaSeries(values, period);
  const upper: Array<number | null> = new Array(values.length).fill(null);
  const lower: Array<number | null> = new Array(values.length).fill(null);
  for (let i = period - 1; i < values.length; i += 1) {
    const slice = values.slice(i - period + 1, i + 1);
    const mean = middle[i] as number;
    const variance = slice.reduce((sum, value) => sum + ((value - mean) ** 2), 0) / period;
    const sd = Math.sqrt(variance);
    upper[i] = mean + sd * deviation;
    lower[i] = mean - sd * deviation;
  }
  return { middle, upper, lower };
}

export function obv(candles: Candle[]) {
  const out: number[] = new Array(candles.length).fill(0);
  for (let i = 1; i < candles.length; i += 1) {
    out[i] = candles[i].close > candles[i - 1].close
      ? out[i - 1] + candles[i].volume
      : candles[i].close < candles[i - 1].close
        ? out[i - 1] - candles[i].volume
        : out[i - 1];
  }
  return out;
}

export function stochastic(candles: Candle[], period = 14, smoothK = 3, smoothD = 3) {
  const rawK: Array<number | null> = new Array(candles.length).fill(null);
  for (let i = period - 1; i < candles.length; i += 1) {
    const slice = candles.slice(i - period + 1, i + 1);
    const highest = Math.max(...slice.map((c) => c.high));
    const lowest = Math.min(...slice.map((c) => c.low));
    rawK[i] = highest === lowest ? 50 : ((candles[i].close - lowest) / (highest - lowest)) * 100;
  }
  const compactK = rawK.map((v) => v ?? Number.NaN);
  const k: Array<number | null> = new Array(candles.length).fill(null);
  for (let i = period - 1 + smoothK - 1; i < candles.length; i += 1) {
    const values = compactK.slice(i - smoothK + 1, i + 1).filter(Number.isFinite);
    if (values.length === smoothK) k[i] = values.reduce((a, b) => a + b, 0) / smoothK;
  }
  const d: Array<number | null> = new Array(candles.length).fill(null);
  for (let i = 0; i < candles.length; i += 1) {
    const values = k.slice(Math.max(0, i - smoothD + 1), i + 1).filter(finite);
    if (values.length === smoothD) d[i] = values.reduce((a, b) => a + b, 0) / smoothD;
  }
  return { k, d };
}

export function adx(candles: Candle[], period = 14) {
  const n = candles.length;
  const tr = new Array<number>(n).fill(0);
  const plusDm = new Array<number>(n).fill(0);
  const minusDm = new Array<number>(n).fill(0);
  for (let i = 1; i < n; i += 1) {
    const up = candles[i].high - candles[i - 1].high;
    const down = candles[i - 1].low - candles[i].low;
    plusDm[i] = up > down && up > 0 ? up : 0;
    minusDm[i] = down > up && down > 0 ? down : 0;
    tr[i] = Math.max(
      candles[i].high - candles[i].low,
      Math.abs(candles[i].high - candles[i - 1].close),
      Math.abs(candles[i].low - candles[i - 1].close),
    );
  }
  const plusDi: Array<number | null> = new Array(n).fill(null);
  const minusDi: Array<number | null> = new Array(n).fill(null);
  const dx: Array<number | null> = new Array(n).fill(null);
  if (n <= period * 2) return { adx: new Array<number | null>(n).fill(null), plusDi, minusDi };
  let trSm = tr.slice(1, period + 1).reduce((a, b) => a + b, 0);
  let plusSm = plusDm.slice(1, period + 1).reduce((a, b) => a + b, 0);
  let minusSm = minusDm.slice(1, period + 1).reduce((a, b) => a + b, 0);
  for (let i = period; i < n; i += 1) {
    if (i > period) {
      trSm = trSm - trSm / period + tr[i];
      plusSm = plusSm - plusSm / period + plusDm[i];
      minusSm = minusSm - minusSm / period + minusDm[i];
    }
    if (trSm <= 0) continue;
    plusDi[i] = (plusSm / trSm) * 100;
    minusDi[i] = (minusSm / trSm) * 100;
    const denom = (plusDi[i] as number) + (minusDi[i] as number);
    dx[i] = denom === 0 ? 0 : (Math.abs((plusDi[i] as number) - (minusDi[i] as number)) / denom) * 100;
  }
  const adxOut: Array<number | null> = new Array(n).fill(null);
  const firstDx = dx.slice(period, period * 2).filter(finite);
  if (firstDx.length === period) {
    let current = firstDx.reduce((a, b) => a + b, 0) / period;
    adxOut[period * 2 - 1] = current;
    for (let i = period * 2; i < n; i += 1) {
      if (!finite(dx[i])) continue;
      current = ((current * (period - 1)) + (dx[i] as number)) / period;
      adxOut[i] = current;
    }
  }
  return { adx: adxOut, plusDi, minusDi };
}

export function buildEvidenceScore(input: MarketScoreInput) {
  let score = 0;
  const reasons: string[] = [];

  if (finite(input.ema20) && finite(input.ema50)) {
    if (input.price > input.ema20 && input.ema20 > input.ema50) { score += 2; reasons.push('Fiyat EMA20 üzerinde ve EMA20 > EMA50.'); }
    else if (input.price < input.ema20 && input.ema20 < input.ema50) { score -= 2; reasons.push('Fiyat EMA20 altında ve EMA20 < EMA50.'); }
  }
  if (finite(input.sma200)) {
    if (input.price > input.sma200) { score += 1; reasons.push('Fiyat SMA200 üzerinde.'); }
    else { score -= 1; reasons.push('Fiyat SMA200 altında.'); }
  }
  if (finite(input.rsi14)) {
    if (input.rsi14 >= 50 && input.rsi14 <= 70) score += 1;
    else if (input.rsi14 < 40) score -= 1;
  }
  if (finite(input.macdHistogram)) score += input.macdHistogram > 0 ? 1 : input.macdHistogram < 0 ? -1 : 0;
  if (finite(input.volumeRatio)) {
    if (input.volumeRatio >= 1.2) score += 1;
    else if (input.volumeRatio <= 0.7) score -= 1;
  }
  if (finite(input.support) && finite(input.resistance)) {
    const toSupport = Math.abs((input.price - input.support) / input.price);
    const toResistance = Math.abs((input.resistance - input.price) / input.price);
    if (toSupport < toResistance * 0.7) score += 1;
    else if (toResistance < toSupport * 0.7) score -= 1;
  }
  if (finite(input.atr14) && input.atr14 / input.price > 0.06) { score -= 1; reasons.push('ATR/fiyat yüksek; volatilite riski artmış.'); }
  if (input.eventUncertainty) { score -= 1; reasons.push('Güncel olay/KAP belirsizliği var.'); }

  const bounded = Math.max(-8, Math.min(8, score));
  const label = bounded >= 4 ? 'teknik görünüm güçlü' : bounded <= -4 ? 'teknik görünüm zayıf' : 'teknik görünüm karışık';
  return { score: bounded, maxAbsScore: 8, label, reasons };
}

export function buildRiskPlan(input: {
  portfolioValue: number;
  maxRiskPercent: number;
  entry: number;
  stop: number;
  targets?: number[];
}) {
  const { portfolioValue, maxRiskPercent, entry, stop, targets = [] } = input;
  if (![portfolioValue, maxRiskPercent, entry, stop].every((v) => Number.isFinite(v) && v > 0)) throw new Error('invalid_input');
  const perUnitRisk = Math.abs(entry - stop);
  if (perUnitRisk <= 0) throw new Error('invalid_stop');
  const monetaryRisk = portfolioValue * (maxRiskPercent / 100);
  const quantity = Math.floor(monetaryRisk / perUnitRisk);
  const positionValue = quantity * entry;
  const rr = targets.map((target) => ({ target, ratio: Math.abs(target - entry) / perUnitRisk }));
  return {
    monetaryRisk,
    perUnitRisk,
    quantity,
    positionValue,
    portfolioWeightPercent: portfolioValue > 0 ? (positionValue / portfolioValue) * 100 : null,
    targetRiskReward: rr,
    assumptions: ['Komisyon/vergi/kayma dahil değildir.', 'Stop gerçekleşme fiyatı garanti değildir.', 'Kullanıcının verdiği risk yüzdesi aynen kullanılmıştır.'],
  };
}
