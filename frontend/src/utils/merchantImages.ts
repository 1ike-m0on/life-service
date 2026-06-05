const curatedImages = [
  '/assets/merchants/hotpot/red-flame-cover.jpg',
  '/assets/merchants/hotpot/shanhai-cover.jpg',
  '/assets/merchants/coffee/moonlight-cover.jpg',
  '/assets/merchants/coffee/riverbank-cover.jpg',
  '/assets/merchants/bakery/morning-wheat-cover.jpg',
  '/assets/merchants/bakery/sweet-oven-cover.jpg',
  '/assets/merchants/japanese/sora-sushi-cover.jpg',
  '/assets/merchants/japanese/kyoto-bento-cover.jpg',
  '/assets/merchants/lifestyle/starlight-cinema-cover.jpg',
  '/assets/merchants/lifestyle/urban-fit-cover.jpg',
];

export function parseMerchantImages(src?: string | null): string[] {
  return src?.split(',')
    .map((item) => item.trim())
    .filter(Boolean) || [];
}

export function merchantFallbackImages(seed = 0, count = 5): string[] {
  return Array.from({ length: Math.min(count, curatedImages.length) }, (_, index) => (
    curatedImages[(seed + index) % curatedImages.length]
  ));
}

export function merchantCoverImage(src?: string | null, seed = 0): string {
  const parsed = parseMerchantImages(src);
  return parsed[0] || merchantFallbackImages(seed, 1)[0];
}

export function merchantGalleryImages(src?: string | null, seed = 0, count = 5): string[] {
  const parsed = parseMerchantImages(src);
  const fallback = merchantFallbackImages(seed, count);
  return [...parsed, ...fallback.filter((image) => !parsed.includes(image))].slice(0, count);
}
