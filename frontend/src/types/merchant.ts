export interface MerchantCategory {
  id: number;
  name: string;
  iconUrl?: string | null;
  sortOrder: number;
  status: number;
}

export interface Merchant {
  id: number;
  categoryId: number;
  name: string;
  images?: string | null;
  area: string;
  address: string;
  longitude?: number | null;
  latitude?: number | null;
  avgPriceCent: number;
  soldCount: number;
  commentCount: number;
  score: number;
  openHours: string;
  status: number;
}
