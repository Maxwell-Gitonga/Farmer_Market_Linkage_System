// Types mirroring the Spring Boot REST API DTOs.
export type Role = 'FARMER' | 'BUYER' | 'ADMIN';
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'BANNED';
export type ProductStatus = 'DRAFT' | 'AVAILABLE' | 'SOLD' | 'REMOVED';
export type TransactionStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
export type Trend = 'RISING' | 'STABLE' | 'FALLING';

export interface UserView {
  id: number; role: Role; name: string; phone: string | null; email: string | null; location: string | null;
  preferences: string | null; status: AccountStatus; createdAt: string; lastLoginAt: string | null;
}
export interface AuthResponse { token: string; user: UserView; }

export interface CropInfo { name: string; emoji: string; shelfLifeDays: number; unit: string; markets: string[]; }
export interface MetaView { crops: CropInfo[]; counties: string[]; }

export interface ProductView {
  id: number; cropType: string; emoji: string; quantity: number; unit: string; harvestDate: string | null;
  location: string; description: string | null; status: ProductStatus; pricePerUnit: number | null;
  recommendedPrice: number | null; farmerId: number; farmerName: string; farmerPhone: string | null;
  farmerLocation: string | null; marketPrice: number | null; priceVsMarketPct: number | null;
  createdAt: string; updatedAt: string;
}
export interface PricePoint { date: string; price: number; }
export interface QuantityPoint { weekStart: string; quantity: number; }
export interface ForecastPoint { date: string; price: number; lower: number | null; upper: number | null; confidenceLabel: string; }

export interface DemandView {
  cropType: string; sufficientData: boolean; trend: Trend | null; changePct: number | null;
  recentWeeklyAvg: number | null; forecastWeeklyAvg: number | null; forecastPeriod: string | null;
  model: string | null; basedOnWeeks: number | null; history: QuantityPoint[]; forecast: QuantityPoint[];
  message: string | null;
}
export interface RecommendationView {
  productId: number | null; cropType: string; county: string; harvestDate: string; quantity: number | null;
  sufficientData: boolean; predictedPrice: number | null; recommendedPrice: number | null;
  lowerBound: number | null; upperBound: number | null; confidence: number | null;
  confidenceLabel: 'high' | 'medium' | 'low' | null; basis: string | null; basedOnRecords: number | null;
  recentRecords: number | null; message: string | null; modelVersion: string | null; algorithm: string | null;
  demand: DemandView | null; decisionPath: string | null; adjustmentPct: number | null; advice: string[];
  bestSellDate: string | null; bestSellPrice: number | null; sellWindow: PricePoint[]; priceTrend: PricePoint[];
  priceTrendBasis: string | null; fallback: boolean; generatedAt: string;
}
export interface ListingCreatedResponse { product: ProductView; recommendation: RecommendationView; }
export interface MatchView {
  buyerId: number; buyerName: string; buyerLocation: string | null; buyerPhone: string | null; score: number;
  reasons: string[]; productId: number; cropType: string;
}
export interface PublishResponse { product: ProductView; matchedBuyers: MatchView[]; }
export interface ProductDetailView { product: ProductView; priceTrend: PricePoint[]; trendBasis: string; }
export interface RecommendedProductView { product: ProductView; score: number; reasons: string[]; }

export interface TransactionView {
  id: number; productId: number; cropType: string; quantity: number; unit: string; agreedPrice: number; total: number;
  status: TransactionStatus; transactionDate: string; farmerId: number; farmerName: string; farmerPhone: string | null;
  buyerId: number; buyerName: string; buyerPhone: string | null;
}
export interface PurchaseResponse { transactionId: number; message: string; transaction: TransactionView; }

export interface ThreadView {
  counterpartId: number; counterpartName: string; counterpartLocation: string | null; counterpartPhone: string | null;
  lastMessage: string; lastAt: string; unread: number;
}
export interface MessageView {
  id: number; senderRole: Role; senderName: string; content: string; createdAt: string; productId: number | null;
  productLabel: string | null; mine: boolean;
}

export interface FarmerDashboard {
  accountStatus: AccountStatus; recommendedPriceCrop: string | null; recommendedPrice: number | null;
  recommendedConfidence: string | null; demandCrop: string | null; demandTrend: Trend | null; demandChangePct: number | null;
  activeListings: number; draftListings: number; listings: ProductView[]; matchedBuyers: MatchView[];
  recentSales: TransactionView[]; salesLast30Days: number; pendingOrders: number;
}

export interface LatestPriceView { cropType: string; market: string; price: number; date: string; change7dPct: number | null; }
export interface PriceForecastView { cropType: string; county: string; history: PricePoint[]; forecast: ForecastPoint[]; message: string | null; }
export interface MarketOption {
  rank: number; market: string; predictedPrice: number; distanceKm: number | null; transportCostPerKg: number | null;
  netPricePerKg: number; confidenceLabel: string; estimatedTotal: number | null;
}

export interface AdminOverview {
  farmers: number; pendingFarmers: number; buyers: number; activeListings: number; soldListings: number;
  removedListings: number; transactions30d: number; volume30dKes: number; predictions30d: number; logs24h: number;
  marketPriceRecords: number; latestMarketDate: string | null; ml: { status: string; retraining: boolean };
}
export interface AdminUserView {
  id: number; role: Role; name: string; phone: string | null; email: string | null; location: string | null;
  status: AccountStatus; createdAt: string; lastLoginAt: string | null; preferences: string | null;
}
export interface LogView {
  id: number; createdAt: string; actorRole: Role | null; actorId: number | null; actorName: string | null;
  action: string; details: string | null;
}
export interface CropSummary { cropType: string; transactions: number; quantity: number; value: number; }
export interface MonthlySummary { month: string; transactions: number; value: number; }
export interface ReportView {
  period: string; byCrop: CropSummary[]; monthly: MonthlySummary[]; usersByCounty: Record<string, number>;
  listingsByStatus: Record<string, number>; transactionsByStatus: Record<string, number>; modelMetrics: any;
}
export interface ImportResult { imported: number; skipped: number; errors: string[]; }
