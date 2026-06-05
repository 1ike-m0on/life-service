export interface LifeNote {
  id: number;
  merchantId: number;
  categoryId: number;
  title: string;
  excerpt: string;
  image: string;
  images?: string[];
  author: string;
  avatar: string;
  likes: number;
  comments: number;
  favorites?: number;
  area: string;
  merchantName: string;
  tags: string[];
  rating?: number;
  createdAt?: string;
}

export const lifeNotes: LifeNote[] = [
  {
    id: 101,
    merchantId: 1001,
    categoryId: 2,
    title: '朋友聚餐点这家，红油锅底真的很香',
    excerpt: '红油锅很稳，毛肚和虾滑都新鲜，工作日晚餐不用排太久。',
    image: '/assets/merchants/hotpot/red-flame-cover.jpg',
    images: ['/assets/merchants/hotpot/red-flame-cover.jpg', '/assets/merchants/hotpot/red-flame-01.jpg'],
    author: '阿茶去吃饭',
    avatar: '',
    likes: 262,
    comments: 26,
    favorites: 84,
    area: '湖滨',
    merchantName: '红焰牛油火锅',
    tags: ['火锅', '朋友聚餐', '晚餐'],
    rating: 5,
  },
  {
    id: 102,
    merchantId: 1002,
    categoryId: 1,
    title: '这家咖啡店适合下午坐一会儿',
    excerpt: '靠窗位置很舒服，拿铁比较顺，甜点不腻，适合带电脑来待两个小时。',
    image: '/assets/merchants/coffee/moonlight-cover.jpg',
    images: ['/assets/merchants/coffee/moonlight-cover.jpg', '/assets/merchants/coffee/moonlight-01.jpg'],
    author: '月亮不加糖',
    avatar: '',
    likes: 171,
    comments: 18,
    favorites: 65,
    area: '武林',
    merchantName: '月见咖啡',
    tags: ['咖啡', '下午茶', '独处'],
    rating: 5,
  },
  {
    id: 103,
    merchantId: 1003,
    categoryId: 3,
    title: '晨麦的碱水包和可颂，早上去更全',
    excerpt: '刚出炉的时候外壳很脆，买两三个当早餐刚好，附近上班族很多。',
    image: '/assets/merchants/bakery/morning-wheat-cover.jpg',
    images: ['/assets/merchants/bakery/morning-wheat-cover.jpg', '/assets/merchants/bakery/morning-wheat-01.jpg'],
    author: '面包雷达',
    avatar: '',
    likes: 98,
    comments: 9,
    favorites: 32,
    area: '黄龙',
    merchantName: '晨麦面包房',
    tags: ['烘焙', '早餐', '可颂'],
    rating: 4,
  },
  {
    id: 104,
    merchantId: 1004,
    categoryId: 4,
    title: '午市定食可以闭眼点，鳗鱼饭很稳',
    excerpt: '米饭软硬刚好，酱汁不会太甜，午休时间来吃很省心。',
    image: '/assets/merchants/japanese/sora-sushi-cover.jpg',
    images: ['/assets/merchants/japanese/sora-sushi-cover.jpg', '/assets/merchants/japanese/sora-sushi-01.jpg'],
    author: '小川今天吃什么',
    avatar: '',
    likes: 214,
    comments: 22,
    favorites: 76,
    area: '滨江',
    merchantName: '空庭寿司',
    tags: ['日料', '午餐', '定食'],
    rating: 5,
  },
  {
    id: 105,
    merchantId: 1005,
    categoryId: 6,
    title: '周末看电影前先看套餐，爆米花组合划算',
    excerpt: '影厅座位维护得还不错，周末人多，最好提前一点到。',
    image: '/assets/merchants/lifestyle/starlight-cinema-cover.jpg',
    images: ['/assets/merchants/lifestyle/starlight-cinema-cover.jpg', '/assets/merchants/lifestyle/starlight-cinema-01.jpg'],
    author: '周末放映员',
    avatar: '',
    likes: 76,
    comments: 7,
    favorites: 21,
    area: '城西',
    merchantName: '星河影城',
    tags: ['电影', '周末', '情侣'],
    rating: 4,
  },
  {
    id: 106,
    merchantId: 1006,
    categoryId: 5,
    title: '第一次体验课，比想象中轻松',
    excerpt: '教练不会硬推课，器械比较新，下班后人稍微多一点。',
    image: '/assets/merchants/lifestyle/urban-fit-cover.jpg',
    images: ['/assets/merchants/lifestyle/urban-fit-cover.jpg', '/assets/merchants/lifestyle/urban-fit-01.jpg'],
    author: '慢慢恢复运动',
    avatar: '',
    likes: 54,
    comments: 5,
    favorites: 14,
    area: '钱江新城',
    merchantName: 'Urban Fit',
    tags: ['健身', '体验课', '下班后'],
    rating: 4,
  },
  {
    id: 107,
    merchantId: 1001,
    categoryId: 2,
    title: '山海铜锅的羊肉很香，适合冬天',
    excerpt: '清汤锅底越煮越香，芝麻酱调得不错，两个人点套餐刚好。',
    image: '/assets/merchants/hotpot/shanhai-cover.jpg',
    images: ['/assets/merchants/hotpot/shanhai-cover.jpg'],
    author: '本地火锅地图',
    avatar: '',
    likes: 189,
    comments: 16,
    favorites: 58,
    area: '上城',
    merchantName: '山海铜锅涮肉',
    tags: ['铜锅', '羊肉', '冬天'],
    rating: 5,
  },
  {
    id: 108,
    merchantId: 1002,
    categoryId: 1,
    title: '河边咖啡的露台位，要赶在傍晚前',
    excerpt: '光线很好，手冲偏清爽，适合饭后散步过来坐一下。',
    image: '/assets/merchants/coffee/riverbank-cover.jpg',
    images: ['/assets/merchants/coffee/riverbank-cover.jpg'],
    author: '沿河散步',
    avatar: '',
    likes: 132,
    comments: 11,
    favorites: 49,
    area: '西溪',
    merchantName: '河岸咖啡',
    tags: ['咖啡', '露台', '傍晚'],
    rating: 4,
  },
];

export const featuredNotes = lifeNotes.slice(0, 6);

const detailParagraphs: Record<number, string[]> = {
  101: [
    '这家更适合晚上和朋友来，锅底香气很足，刚上桌的时候牛油味就能闻到。毛肚、虾滑和鸭血是我觉得比较稳的几样，两三个人点一个套餐再加两份菜刚好。',
    '排队情况比想象中轻松，工作日晚餐早点到基本不用等太久。座位间距还可以，聊天不费劲，服务员加汤也比较及时。',
    '第一次来建议先点微辣或中辣。锅底越煮越入味，后半段涮素菜更好吃。人均不算低，但聚餐氛围和出品都比较稳。',
  ],
  102: [
    '下午来最舒服，靠窗位置光线好，带电脑坐一会儿也不会突兀。拿铁口感顺，甜点不太甜，适合饭后或工作间隙过来放空。',
    '店里音乐音量不大，聊天和办公都还行。高峰时段座位会紧一点，想坐窗边最好避开周末下午。',
  ],
  103: [
    '晨麦更适合早上去，碱水包和可颂刚出炉的时候口感最好。附近上班族不少，九点前品类更全，晚一点热门款会少很多。',
    '可颂外壳比较脆，黄油香明显但不腻。想买早餐的话，两三个面包加一杯咖啡就很省心。',
  ],
  104: [
    '午市定食是省心选择，鳗鱼饭酱汁不会过甜，米饭软硬刚好。一个人吃也舒服，出餐速度稳定。',
    '环境比普通快餐店安静，适合午休时间来。晚餐可以看套餐，价格会比单点友好一些。',
  ],
};

export function notesForMerchant(merchantId: number): LifeNote[] {
  const matched = lifeNotes.filter((note) => note.merchantId === merchantId);
  return matched.length > 0 ? matched : lifeNotes.slice(0, 4);
}

export function notesForCategory(categoryId?: number | null): LifeNote[] {
  return categoryId ? lifeNotes.filter((note) => note.categoryId === categoryId) : lifeNotes;
}

export function noteById(noteId: number): LifeNote | null {
  return lifeNotes.find((note) => note.id === noteId) || null;
}

export function noteContent(noteId: number): string[] {
  const note = noteById(noteId);
  if (!note) {
    return [];
  }
  return detailParagraphs[noteId] || [note.excerpt];
}

export function relatedNotesForNote(noteId: number, limit = 4): LifeNote[] {
  const current = noteById(noteId);
  if (!current) {
    return lifeNotes.slice(0, limit);
  }
  const sameCategory = lifeNotes.filter((note) => note.categoryId === current.categoryId && note.id !== noteId);
  const others = lifeNotes.filter((note) => note.categoryId !== current.categoryId && note.id !== noteId);
  return [...sameCategory, ...others].slice(0, limit);
}
