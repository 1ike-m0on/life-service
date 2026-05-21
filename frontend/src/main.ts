import { createPinia } from 'pinia';
import Vant from 'vant';
import { createApp } from 'vue';
import 'vant/lib/index.css';
import App from './App.vue';
import router from './router';
import './styles/theme.css';
import './styles/index.css';

createApp(App)
  .use(createPinia())
  .use(router)
  .use(Vant)
  .mount('#app');
