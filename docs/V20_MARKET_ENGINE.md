# İbrahim AI V20 — Market Intelligence Engine

Status: staged for the web/PWA assistant and native shell. The current AppDeploy web deployment is quota-blocked, so this branch preserves the next production upgrade without pretending it is already live.

## Product rules

- Every market answer starts with `Veri zamanı` and `Kaynak`.
- Never invent price, support, resistance, indicator, balance-sheet or news data.
- Current/changing financial facts require fresh tools; model memory is not evidence.
- Support/resistance are zones, not guarantees.
- Separate observed data from interpretation.
- No guaranteed-profit language. Every trade plan must include invalidation, risk, and data freshness.
- Prefer KAP/company IR/Borsa İstanbul/regulators for fundamentals and corporate events; use reputable independent reporting for context.
- If sources disagree, show the disagreement and weight by authority/date rather than averaging blindly.

## V20 analysis stack

### 1. Multi-timeframe confirmation

Analyze at least intraday + daily + swing context when data exists. Return a compact matrix:

- Trend: 5m/15m/1h/1d context where provider supports it
- Momentum: RSI + MACD
- Structure: nearest support/resistance zones
- Volatility: ATR
- Volume confirmation: current/average ratio
- Freshness: age of latest datapoint

Do not collapse conflicting timeframes into one fake certainty. Mark `UYUMLU`, `KARIŞIK`, or `TERS`.

### 2. Expanded indicators

Add code-computed indicators instead of asking the model to estimate them:

- Bollinger Bands 20/2
- ADX 14 (+DI/-DI where practical)
- OBV
- Stochastic 14,3,3
- RSI 14
- MACD 12,26,9
- SMA 20/50/200 when history allows
- EMA 20/50
- ATR 14
- 20-period volume average and volume ratio

Each indicator must expose raw values plus a plain-language interpretation. A single indicator can never decide the final view.

### 3. Signal matrix — evidence score, not buy/sell certainty

Create a deterministic score only from verified inputs. Example dimensions:

- trend structure: -2..+2
- momentum: -2..+2
- volume confirmation: -1..+1
- volatility/risk penalty: -1..0
- support/resistance location: -2..+2
- event/news uncertainty penalty: -2..0

Output labels such as `teknik görünüm güçlü/zayıf/karışık`; never `kesin al` or a fake win probability.

### 4. Risk engine

For a user-provided portfolio size, entry and max risk percentage, calculate deterministically:

- monetary risk budget
- ATR-aware stop distance
- candidate invalidation level
- position size
- target levels
- risk/reward ratios
- concentration after the trade

Do not choose a risk percentage for the user unless they explicitly request a suggestion; clearly show assumptions.

### 5. Fundamental/KAP research checklist

For BIST/company analysis, research and distinguish:

- latest financial statements and period
- revenue / EBITDA / net income trend
- margins
- net debt/cash and leverage when available
- operating cash flow / free cash flow when available
- share count / capital actions
- dividends
- KAP material events
- management guidance / investor-relations statements
- sector-specific drivers
- valuation metrics only when source inputs are current and comparable

For every important current claim, prefer direct pages/documents. Search-result snippets are discovery only.

### 6. Three-scenario decision frame

Market answers should produce `Olumlu / Nötr / Olumsuz` scenarios with observable triggers instead of one-direction predictions.

Each scenario contains:

- trigger/confirmation
- invalidation
- nearby levels
- what new data would change the view

### 7. Portfolio analyzer

Add a user-owned portfolio store with symbol, quantity, average cost and optional thesis. Analyze:

- current P/L from fresh price data
- weight by holding
- concentration
- correlated/sector concentration when known
- distance to technical invalidation
- aggregate risk budget
- which holdings need fresh KAP/news review

Never silently overwrite cost data. Market prices/news are not stored as durable memories.

### 8. Research/audit hardening

For finance/high-impact answers:

1. planner forces DEEP + CURRENT_REQUIRED
2. price/technical claims use market tool
3. company/news claims research 3 direct sources where available
4. KAP/official source is preferred
5. calculator handles arithmetic
6. critic audits numbers, dates, claim-to-source match and unsupported certainty
7. final answer exposes verification status and source count

Explicit user requests such as `araştır`, `kontrol et`, `kaynaklara bak`, `güncel bak`, `doğrula`, `derin analiz` must force research.

### 9. Durable learning

Background learning may generalize explicit corrections and stable preferences, e.g. `bundan sonra`, `tercihim`, `istemiyorum`, `hep böyle yap`.

Never learn/store as a durable rule:

- passwords/secrets/API keys
- transient prices/news
- one-off trade calls
- unverified claims

### 10. Clean launch + performance

- Every app launch opens a blank new-chat view; history remains in menu.
- Bootstrap returns chat metadata only; messages load lazily when a chat is opened.
- Avoid preloading heavy market panels.
- Cache only short-lived market responses with explicit timestamps; never present cache as fresh.

## V20 target answer structure for stock analysis

1. Veri zamanı / kaynak
2. Kısa sonuç
3. Fiyat + trend structure
4. Multi-timeframe matrix
5. Indicators
6. Support/resistance zones
7. Volume/volatility
8. KAP/fundamental/news findings
9. Bull/base/bear scenarios
10. Risk / invalidation / RR
11. What would change the view
12. Verification status + strongest sources

The assistant must remain useful for investing decisions without claiming certainty or replacing licensed real-time exchange data.