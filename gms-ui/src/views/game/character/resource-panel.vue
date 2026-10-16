<template>
  <section class="resource-panel">
    <a-form :model="form" layout="vertical" class="resource-grid" :disabled="busy">
      <a-form-item :label="t('account.player.form.type')">
        <a-select v-model="form.type" :options="options" @change="resetFields" />
      </a-form-item>
      <a-form-item v-if="form.type === 5 || form.type === 6" :label="t('account.player.form.id')">
        <a-input-number v-model="form.id" :min="1" :precision="0" @change="itemChanged" />
      </a-form-item>
      <a-form-item v-if="form.type < 6 || form.type >= 11" :label="t('account.player.form.quantity')">
        <a-input-number v-model="form.quantity" :precision="0" />
      </a-form-item>
      <a-form-item v-if="form.type > 6 && form.type < 11" :label="t('account.player.form.rate')">
        <a-input-number v-model="form.rate" :min="0" />
      </a-form-item>
      <template v-if="form.type === 6">
        <a-form-item v-for="field in equipmentFields" :key="field" :label="t('account.player.form.' + field)">
          <a-input-number v-model="form[field]" :precision="0" :placeholder="field === 'expire' ? t('account.player.form.expire.placeholder') : undefined" />
        </a-form-item>
      </template>
    </a-form>
    <a-space wrap>
      <a-button type="primary" :loading="busy" :disabled="!valid" @click="submit"><template #icon><icon-plus /></template>{{ t('account.player.give') }}</a-button>
      <a-button :disabled="busy" @click="reset"><template #icon><icon-undo /></template>{{ t('button.reset') }}</a-button>
      <a-button :loading="historyLoading" @click="loadHistory"><template #icon><icon-history /></template>{{ t('account.player.grant.history') }}</a-button>
    </a-space>
    <a-table v-if="showHistory" :data="history" :loading="historyLoading" :pagination="{pageSize:10}" :scroll="{x:700}" row-key="id" class="reward-history">
      <template #columns>
        <a-table-column title="ID" data-index="id" :width="60" />
        <a-table-column :title="t('account.player.form.type')" :width="220"><template #cell="{record}">{{ resourceLabel(record.resource.type) }}<span v-if="record.resource.id"> · {{ record.resource.id }}</span> ×{{ record.resource.type === 6 ? 1 : record.resource.quantity }}</template></a-table-column>
        <a-table-column :title="t('characterAdmin.status')" :width="90"><template #cell="{record}"><a-tag :color="record.status === 'delivered' ? 'green' : 'orange'">{{ t('account.player.grant.' + record.status) }}</a-tag></template></a-table-column>
        <a-table-column :title="t('account.player.grant.created')" data-index="createdAt" :width="160" />
        <a-table-column :title="t('account.player.grant.deliveredAt')" data-index="deliveredAt" :width="160" />
      </template>
    </a-table>
  </section>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { Message } from '@arco-design/web-vue';
  import { GiveForm, getEquInitialInfo, getRewardHistory, givePlayerSrc } from '@/api/player';

  const props = defineProps<{ cid: number; name: string; world: number; online: boolean }>();
  const emit = defineEmits<{ (event: 'busy', value: boolean): void }>();
  const { t } = useI18n();
  const resourceKeys = ['nxCredit','nxPrepaid','maplePoint','mesos','exp','item','equip','expRate','mesosRate','dropRate','bossRate','gm','fame'];
  const equipmentFields = ['str','dex','int','luk','hp','mp','pAtk','mAtk','pDef','mDef','acc','avoid','hands','speed','jump','upgradeSlot','expire'] as const;
  const equipmentKeys: Record<string,string> = {pAtk:'patk',mAtk:'matk',pDef:'pdef',mDef:'mdef'};
  const options = computed(() => resourceKeys.map((key,value) => ({value,label:t('account.player.' + key)})).filter(option => option.value !== 10 && (props.online || option.value <= 6)));
  const form = ref<GiveForm>({type:0});
  const busy = ref(false);
  const historyLoading = ref(false);
  const showHistory = ref(false);
  const history = ref<any[]>([]);
  let generation = 0;
  let historyGeneration = 0;
  let equipmentGeneration = 0;
  const valid = computed(() => props.cid > 0 && options.value.some(option => option.value === form.value.type) &&
    (form.value.type === 6 ? !!form.value.id : form.value.type > 6 && form.value.type < 11 ? form.value.rate !== undefined : form.value.quantity !== undefined) &&
    (!(form.value.type === 5 || form.value.type === 6) || !!form.value.id));
  function resourceLabel(type: number) { return t('account.player.' + resourceKeys[type]); }
  function resetFields() {
    equipmentGeneration += 1;
    form.value = {type:form.value.type, deliveryId:crypto.randomUUID(), playerId:props.cid, player:props.name, worldId:props.world};
  }
  function reset() { form.value.type = 0; resetFields(); }
  watch(() => [props.cid, props.world], () => {
    generation += 1; historyGeneration += 1; history.value = []; showHistory.value = false; reset();
  }, {immediate:true});
  watch(() => props.online, () => { if (!options.value.some(option => option.value === form.value.type)) reset(); });
  watch(busy, value => emit('busy', value));
  async function itemChanged() {
    const token = ++equipmentGeneration;
    if (form.value.type !== 6 || !form.value.id) return;
    const id = form.value.id;
    busy.value = true;
    try {
      const { data } = await getEquInitialInfo(id);
      if (token !== equipmentGeneration || form.value.id !== id) return;
      equipmentFields.forEach(field => { form.value[field] = data[equipmentKeys[field] || field]; });
    } finally { busy.value = false; }
  }
  async function loadHistory() {
    const token = ++historyGeneration;
    const cid = props.cid;
    showHistory.value = true; historyLoading.value = true;
    try { const {data} = await getRewardHistory(cid); if (token === historyGeneration) history.value = data; }
    finally { if (token === historyGeneration) historyLoading.value = false; }
  }
  async function submit() {
    if (busy.value || !valid.value) return;
    const token = generation;
    const payload = {...form.value, playerId:props.cid, player:props.name, worldId:props.world};
    busy.value = true;
    try {
      const {data} = await givePlayerSrc(payload);
      Message.success(t(data.queued ? 'account.player.grant.queued' : 'message.success'));
      if (token === generation) { resetFields(); await loadHistory(); }
    } finally { busy.value = false; }
  }
  onBeforeUnmount(() => { generation += 1; historyGeneration += 1; equipmentGeneration += 1; emit('busy', false); });
</script>

<style scoped lang="less">
  .resource-panel { padding:16px 0; }
  .resource-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:0 24px; }
  .reward-history { margin-top:20px; }
  @media (max-width:900px) { .resource-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
  @media (max-width:600px) { .resource-grid { grid-template-columns:minmax(0,1fr); } }
</style>
