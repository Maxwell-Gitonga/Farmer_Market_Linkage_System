import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import {
  AdminOverview, AdminUserView, AccountStatus, AuthResponse, DemandView, FarmerDashboard, ImportResult,
  LatestPriceView, ListingCreatedResponse, LogView, MarketOption, MatchView, MessageView, MetaView, PriceForecastView,
  PricePoint, ProductDetailView, ProductStatus, ProductView, PublishResponse, PurchaseResponse, RecommendationView,
  RecommendedProductView, ReportView, Role, ThreadView, TransactionStatus, TransactionView, UserView
} from './models';

/** REST client for the Spring Boot backend (all calls go through /api, proxied to :8080 in development). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api';
  private meta$?: Observable<MetaView>;

  private params(obj: Record<string, any>): HttpParams {
    let p = new HttpParams();
    Object.entries(obj).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    });
    return p;
  }

  // ---- auth
  login(phone: string, password: string, role?: Role) {
    return this.http.post<AuthResponse>(`${this.base}/auth/login`, { phone, password, role });
  }
  register(body: any) { return this.http.post<AuthResponse>(`${this.base}/auth/register`, body); }
  me() { return this.http.get<UserView>(`${this.base}/auth/me`); }
  updateProfile(body: any) { return this.http.put<UserView>(`${this.base}/auth/me`, body); }

  // ---- reference data
  meta(): Observable<MetaView> {
    if (!this.meta$) this.meta$ = this.http.get<MetaView>(`${this.base}/market/meta`).pipe(shareReplay(1));
    return this.meta$;
  }

  // ---- market insights
  latestPrices(crop?: string) { return this.http.get<LatestPriceView[]>(`${this.base}/market/prices/latest`, { params: this.params({ crop }) }); }
  priceHistory(crop: string, county?: string, days = 90) {
    return this.http.get<PricePoint[]>(`${this.base}/market/prices/history`, { params: this.params({ crop, county, days }) });
  }
  priceForecast(crop: string, county: string, weeks = 4) {
    return this.http.get<PriceForecastView>(`${this.base}/market/forecast`, { params: this.params({ crop, county, weeks }) });
  }
  demand(crop: string) { return this.http.get<DemandView>(`${this.base}/market/demand`, { params: this.params({ crop }) }); }
  bestMarkets(crop: string, county: string, quantity?: number | null, date?: string | null) {
    return this.http.get<MarketOption[]>(`${this.base}/market/best-markets`, { params: this.params({ crop, county, quantity, date }) });
  }
  quickEstimate(body: { cropType: string; location: string; harvestDate?: string | null; quantity?: number | null }) {
    return this.http.post<RecommendationView>(`${this.base}/market/quick-estimate`, body);
  }

  // ---- products
  searchProducts(filters: { crop?: string; county?: string; maxPrice?: number | null; sort?: string }) {
    return this.http.get<ProductView[]>(`${this.base}/products`, { params: this.params(filters) });
  }
  product(id: number) { return this.http.get<ProductDetailView>(`${this.base}/products/${id}`); }
  myProducts() { return this.http.get<ProductView[]>(`${this.base}/products/mine`); }
  createProduct(body: any) { return this.http.post<ListingCreatedResponse>(`${this.base}/products`, body); }
  publish(id: number, price: number, acceptedRecommendation: boolean) {
    return this.http.post<PublishResponse>(`${this.base}/products/${id}/publish`, { price, acceptedRecommendation });
  }
  recommendation(id: number, refresh = false) {
    return this.http.get<RecommendationView>(`${this.base}/products/${id}/recommendation`, { params: this.params({ refresh }) });
  }
  matches(id: number) { return this.http.get<MatchView[]>(`${this.base}/products/${id}/matches`); }
  updateProduct(id: number, body: any) { return this.http.put<ProductView>(`${this.base}/products/${id}`, body); }
  deleteProduct(id: number) { return this.http.delete<{ message: string }>(`${this.base}/products/${id}`); }

  // ---- farmer / buyer
  farmerDashboard() { return this.http.get<FarmerDashboard>(`${this.base}/farmer/dashboard`); }
  buyerRecommendations(limit = 6) {
    return this.http.get<RecommendedProductView[]>(`${this.base}/buyer/recommendations`, { params: this.params({ limit }) });
  }

  // ---- transactions
  purchase(productId: number, quantity: number) {
    return this.http.post<PurchaseResponse>(`${this.base}/transactions`, { productId, quantity });
  }
  myTransactions() { return this.http.get<TransactionView[]>(`${this.base}/transactions/mine`); }
  setTransactionStatus(id: number, status: TransactionStatus) {
    return this.http.patch<TransactionView>(`${this.base}/transactions/${id}/status`, { status });
  }

  // ---- messages
  threads() { return this.http.get<ThreadView[]>(`${this.base}/messages/threads`); }
  thread(counterpartId: number) { return this.http.get<MessageView[]>(`${this.base}/messages/thread/${counterpartId}`); }
  sendMessage(counterpartId: number, content: string, productId?: number | null) {
    return this.http.post<MessageView>(`${this.base}/messages`, { counterpartId, content, productId });
  }

  // ---- admin
  adminOverview() { return this.http.get<AdminOverview>(`${this.base}/admin/overview`); }
  adminUsers(role?: Role | '', status?: AccountStatus | '') {
    return this.http.get<AdminUserView[]>(`${this.base}/admin/users`, { params: this.params({ role, status }) });
  }
  setUserStatus(role: Role, id: number, status: AccountStatus, reason?: string) {
    return this.http.patch<AdminUserView>(`${this.base}/admin/users/${role}/${id}/status`, { status, reason });
  }
  adminListings(status?: ProductStatus | '') {
    return this.http.get<ProductView[]>(`${this.base}/admin/listings`, { params: this.params({ status }) });
  }
  removeListing(id: number, reason: string) {
    return this.http.post<ProductView>(`${this.base}/admin/listings/${id}/remove`, { reason });
  }
  logs(action?: string) { return this.http.get<LogView[]>(`${this.base}/admin/logs`, { params: this.params({ action }) }); }
  reports(days = 365) { return this.http.get<ReportView>(`${this.base}/admin/reports`, { params: this.params({ days }) }); }
  exportReport(type: string, days = 90) {
    return this.http.get(`${this.base}/admin/reports/export`, { params: this.params({ type, days }), responseType: 'blob' });
  }
  addMarketPrice(body: any) { return this.http.post<{ priceId: number; message: string }>(`${this.base}/admin/market-prices`, body); }
  importMarketPrices(file: File) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/admin/market-prices/import`, fd);
  }
  mlStatus() { return this.http.get<any>(`${this.base}/admin/ml/status`); }
  retrain() { return this.http.post<{ status: string; message: string }>(`${this.base}/admin/ml/retrain`, {}); }
  health() { return this.http.get<any>(`${this.base}/health`); }
}
