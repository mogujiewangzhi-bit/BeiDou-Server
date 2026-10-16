<template>
  <a-select
    :model-value="modelValue" :options="options" :loading="loading" :disabled="disabled"
    :placeholder="t('characterAdmin.picker')" allow-search allow-clear :search-delay="0" :filter-option="false" :fallback-option="fallbackOption"
    @search="search" @change="choose" @popup-visible-change="opened"
  >
    <template #footer>
      <a-button v-if="hasMore" type="text" long :loading="loading" @click="loadMore">
        {{ t('characterAdmin.more') }}
      </a-button>
    </template>
  </a-select>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { characterOptions, CharacterOption } from '@/api/character-admin';

  const props = defineProps<{ modelValue?: number; world?: number; disabled?: boolean }>();
  const emit = defineEmits<{
    (e: 'update:modelValue', value: number | undefined): void;
    (e: 'select', value: CharacterOption | undefined): void;
  }>();
  const { t } = useI18n();
  const rows = ref<CharacterOption[]>([]);
  const selected = ref<CharacterOption>();
  const loading = ref(false);
  const hasMore = ref(false);
  let keyword = '';
  let page = 1;
  let generation = 0;
  let selectedGeneration = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const label = (row: CharacterOption) => `${row.name} · #${row.id} · ${row.jobName} Lv.${row.level} · ${t(row.online ? 'characterAdmin.online' : 'characterAdmin.offline')}`;
  const options = computed(() => rows.value.map(row => ({value:row.id,label:label(row)})));
  const fallbackOption = (value: any) => {
    const row = selected.value;
    return {value,label:row && row.id === value ? label(row) : String(value)};
  };
  async function load(append = false) {
    const token = ++generation;
    loading.value = true;
    try {
      const { data } = await characterOptions(keyword, props.world, undefined, page);
      if (token !== generation) return;
      rows.value = append ? [...new Map([...rows.value,...data.records].map(row=>[row.id,row])).values()] : data.records;
      hasMore.value = data.hasMore;
    } finally { if (token === generation) loading.value = false; }
  }
  function search(value: string) {
    keyword = value; page = 1; generation += 1;
    rows.value = []; hasMore.value = false; loading.value = true;
    clearTimeout(timer);
    timer = setTimeout(() => { load().catch(() => { /* API interceptor reports errors. */ }); }, 250);
  }
  function loadMore() {
    if (!loading.value && hasMore.value) {
      const nextPage = page + 1; const query = keyword; page = nextPage;
      load(true).catch(() => { if (page === nextPage && keyword === query) page -= 1; });
    }
  }
  function opened(open: boolean) { if (open) { search(''); } }
  function choose(value: any) {
    const id = typeof value === 'number' ? value : undefined;
    selected.value = id === undefined ? undefined : [...rows.value, ...(selected.value ? [selected.value] : [])].find(row => row.id === id);
    emit('update:modelValue', id); emit('select', selected.value);
  }
  async function hydrate(id?: number) {
    const token = ++selectedGeneration;
    if (!id) { selected.value = undefined; return; }
    if (selected.value?.id === id && (props.world === undefined || props.world === selected.value.world)) return;
    try {
      const { data } = await characterOptions('', props.world, id);
      if (token === selectedGeneration) selected.value = data.records[0];
    } catch (error) { if (token === selectedGeneration) selected.value = undefined; }
  }
  watch(() => props.modelValue, id => { hydrate(id).catch(() => {}); });
  watch(() => props.world, () => { search(''); hydrate(props.modelValue).catch(() => {}); });
  onMounted(() => { load().catch(() => {}); hydrate(props.modelValue).catch(() => {}); });
  onBeforeUnmount(() => { clearTimeout(timer); generation += 1; selectedGeneration += 1; });
</script>
