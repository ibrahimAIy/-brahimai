export type PortfolioPosition = {
  symbol: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  sector?: string | null;
  stop?: number | null;
};

export type PortfolioAnalysis = {
  investedCost: number;
  currentValue: number;
  profitLoss: number;
  profitLossPercent: number | null;
  positions: Array<{
    symbol: string;
    currentValue: number;
    costValue: number;
    profitLoss: number;
    profitLossPercent: number | null;
    portfolioWeightPercent: number;
    riskToStop: number | null;
    riskToStopPercentOfPortfolio: number | null;
  }>;
  largestPosition: { symbol: string; weightPercent: number } | null;
  top3WeightPercent: number;
  sectorWeights: Array<{ sector: string; weightPercent: number }>;
  warnings: string[];
};

function validNumber(value: number) {
  return Number.isFinite(value) && value >= 0;
}

export function analyzePortfolio(input: PortfolioPosition[]): PortfolioAnalysis {
  const clean = input.filter((p) => p.symbol.trim() && validNumber(p.quantity) && validNumber(p.averageCost) && validNumber(p.currentPrice));
  const investedCost = clean.reduce((sum, p) => sum + p.quantity * p.averageCost, 0);
  const currentValue = clean.reduce((sum, p) => sum + p.quantity * p.currentPrice, 0);
  const profitLoss = currentValue - investedCost;

  const positions = clean.map((p) => {
    const costValue = p.quantity * p.averageCost;
    const value = p.quantity * p.currentPrice;
    const pl = value - costValue;
    const stopRisk = typeof p.stop === 'number' && Number.isFinite(p.stop) && p.stop > 0
      ? Math.max(0, (p.currentPrice - p.stop) * p.quantity)
      : null;
    return {
      symbol: p.symbol.toUpperCase(),
      currentValue: value,
      costValue,
      profitLoss: pl,
      profitLossPercent: costValue > 0 ? (pl / costValue) * 100 : null,
      portfolioWeightPercent: currentValue > 0 ? (value / currentValue) * 100 : 0,
      riskToStop: stopRisk,
      riskToStopPercentOfPortfolio: stopRisk !== null && currentValue > 0 ? (stopRisk / currentValue) * 100 : null,
    };
  }).sort((a, b) => b.portfolioWeightPercent - a.portfolioWeightPercent);

  const sectorTotals = new Map<string, number>();
  for (const p of clean) {
    const sector = (p.sector || 'Bilinmiyor').trim() || 'Bilinmiyor';
    sectorTotals.set(sector, (sectorTotals.get(sector) || 0) + p.quantity * p.currentPrice);
  }
  const sectorWeights = [...sectorTotals.entries()]
    .map(([sector, value]) => ({ sector, weightPercent: currentValue > 0 ? (value / currentValue) * 100 : 0 }))
    .sort((a, b) => b.weightPercent - a.weightPercent);

  const largestPosition = positions[0]
    ? { symbol: positions[0].symbol, weightPercent: positions[0].portfolioWeightPercent }
    : null;
  const top3WeightPercent = positions.slice(0, 3).reduce((sum, p) => sum + p.portfolioWeightPercent, 0);
  const warnings: string[] = [];
  if (largestPosition && largestPosition.weightPercent >= 35) warnings.push(`Tek hisse yoğunluğu yüksek: ${largestPosition.symbol} portföyün yaklaşık %${largestPosition.weightPercent.toFixed(1)}'i.`);
  if (top3WeightPercent >= 75 && positions.length > 3) warnings.push(`İlk 3 pozisyon portföyün yaklaşık %${top3WeightPercent.toFixed(1)}'ini oluşturuyor.`);
  const largestSector = sectorWeights[0];
  if (largestSector && largestSector.sector !== 'Bilinmiyor' && largestSector.weightPercent >= 50) warnings.push(`Sektör yoğunluğu yüksek: ${largestSector.sector} yaklaşık %${largestSector.weightPercent.toFixed(1)}.`);
  const stopRiskTotal = positions.reduce((sum, p) => sum + (p.riskToStop || 0), 0);
  if (currentValue > 0 && stopRiskTotal / currentValue >= 0.08) warnings.push(`Tanımlı stoplara göre toplam açık risk portföyün yaklaşık %${((stopRiskTotal / currentValue) * 100).toFixed(1)}'i.`);

  return {
    investedCost,
    currentValue,
    profitLoss,
    profitLossPercent: investedCost > 0 ? (profitLoss / investedCost) * 100 : null,
    positions,
    largestPosition,
    top3WeightPercent,
    sectorWeights,
    warnings,
  };
}
