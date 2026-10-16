<template>
  <section class="inventory-panel">
    <a-space wrap>
      <a-select v-model="currentType" :options="types.map(type => ({value:type.inventoryType,label:type.name}))" style="width:200px" />
      <a-button @click="refresh"><template #icon><icon-refresh /></template>{{ t('button.refresh') }}</a-button>
      <a-button type="primary" :disabled="!drawable" @click="visible = true"><template #icon><icon-apps /></template>{{ t('inventory.placeholder.inventoryDraw') }}</a-button>
    </a-space>
    <div class="inventory-table">
      <InventoryList :key="`${cid}:${currentType}:${revision}`" :character-id="cid" :current-type="currentType" />
    </div>
    <a-modal v-model:visible="visible" :title="`${name} · #${cid} — ${currentLabel}`" width="min(800px,94vw)" :footer="false" @close="refresh">
      <div class="inventory-canvas"><InventoryUI v-if="visible" :character-id="cid" :inventory-type="Number(currentType)" /></div>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { getInventoryTypeList } from '@/api/inventory';
  import { InventoryTypeState } from '@/store/modules/inventory/type';
  import InventoryList from '@/views/game/inventory/table.vue';
  import InventoryUI from '@/views/game/inventory/InventoryUI.vue';

  const props = defineProps<{cid:number;name:string}>();
  const {t} = useI18n();
  const types = ref<InventoryTypeState[]>([]);
  const currentType = ref<string | number>(0);
  const visible = ref(false);
  const revision = ref(0);
  const drawable = computed(() => [1,2,3,4,5].includes(Number(currentType.value)));
  const currentLabel = computed(() => types.value.find(type => Number(type.inventoryType) === Number(currentType.value))?.name || '');
  function refresh() { revision.value += 1; }
  watch(() => props.cid, () => {visible.value = false; currentType.value = 0; refresh();});
  getInventoryTypeList().then(({data}) => { types.value = data; });
</script>

<style scoped lang="less">
  .inventory-panel { padding:16px 0; min-width:0; }
  .inventory-table { margin-top:16px; overflow-x:auto; }
  .inventory-table :deep(.arco-table) { min-width:1300px; }
  .inventory-canvas { overflow-x:auto; }
</style>
