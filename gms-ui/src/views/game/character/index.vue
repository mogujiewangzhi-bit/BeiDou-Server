<template>
  <div class="container character-manager">
    <Breadcrumb />
    <section class="manager-body">
      <div class="manager-header">
        <h2>{{ t('menu.game.character') }}</h2>
        <div class="picker"><CharacterPicker v-model="cid" :disabled="busy" @select="loadDetail" /></div>
        <a-tooltip :content="t('button.refresh')">
          <a-button :disabled="!cid || busy" :loading="loading" @click="loadDetail"><icon-refresh /></a-button>
        </a-tooltip>
      </div>
      <a-spin :loading="loading" style="width:100%">
        <a-empty v-if="!detail" />
        <template v-else>
          <a-descriptions :column="{ xs: 1, sm: 2, lg: 4 }" class="identity">
            <a-descriptions-item :label="t('characterAdmin.name')">{{ detail.values.name }} · #{{ cid }}</a-descriptions-item>
            <a-descriptions-item :label="t('characterAdmin.account')">{{ detail.values.accountName }} · #{{ detail.values.accountid }}</a-descriptions-item>
            <a-descriptions-item :label="t('characterAdmin.world')">{{ detail.values.world }}</a-descriptions-item>
            <a-descriptions-item :label="t('characterAdmin.status')"><a-tag :color="detail.online ? 'green' : 'gray'">{{ t(detail.online ? 'characterAdmin.online' : 'characterAdmin.offline') }}</a-tag></a-descriptions-item>
          </a-descriptions>
          <a-alert v-if="!detail.editable" type="warning">{{ t('characterAdmin.locked') }}</a-alert>
          <a-tabs v-model:active-key="activeTab" class="editor-tabs" lazy-load>
            <a-tab-pane v-for="group in groups" :key="group" :title="t('characterAdmin.group.' + group)">
              <a-alert v-if="group === 'account'" type="warning" class="group-alert">{{ t('characterAdmin.accountShared') }}</a-alert>
              <a-form :model="form" layout="vertical" :disabled="!detail.editable || busy" class="attribute-grid">
                <a-form-item v-for="field in detail.fields.filter(f => f.group === group)" :key="field.key" :label="t('characterAdmin.field.' + field.key)" :class="{'sp-field':field.type === 'sp'}">
                  <a-select v-if="field.key === 'job'" v-model="form[field.key]" :options="detail.jobs" allow-search />
                  <a-select v-else-if="field.key === 'gender'" v-model="form[field.key]" :options="[{value:0,label:t('characterAdmin.male')},{value:1,label:t('characterAdmin.female')}]" />
                  <div v-else-if="field.type === 'sp'" class="sp-grid">
                    <a-input-number v-for="index in 10" :key="index" :model-value="spValue(index - 1)" :min="0" :max="32767" :precision="0" @change="setSp(index - 1, $event)"><template #prefix>SP{{ index }}</template></a-input-number>
                  </div>
                  <a-input-number v-else v-model="form[field.key]" :min="field.min" :max="field.max" :precision="0" :step="1" />
                </a-form-item>
              </a-form>
            </a-tab-pane>
            <a-tab-pane key="relations" :title="t('characterAdmin.relations')">
              <a-descriptions :column="{xs:1,sm:2,lg:3}" bordered>
                <a-descriptions-item v-for="key in readOnlyKeys" :key="key" :label="t('characterAdmin.readonly.' + key)">{{ display(detail.values[key]) }}</a-descriptions-item>
              </a-descriptions>
            </a-tab-pane>
            <a-tab-pane key="resources" :title="t('account.player.button.give')">
              <ResourcePanel v-if="cid" :key="cid" :cid="cid" :name="String(detail.values.name)" :world="Number(detail.values.world)" :online="detail.online" @busy="resourceBusy = $event" />
            </a-tab-pane>
            <a-tab-pane key="items" :title="t('menu.game.inventory')">
              <InventoryPanel v-if="activeTab === 'items' && cid" :key="cid" :cid="cid" :name="String(detail.values.name)" />
            </a-tab-pane>
            <a-tab-pane key="skills" :title="t('skillAdmin.title')">
              <SkillsPanel v-if="cid" :key="cid" :cid="cid" :name="String(detail.values.name)" :editable="detail.editable" @busy="skillBusy = $event" />
            </a-tab-pane>
          </a-tabs>
          <div v-if="!['resources','items','skills'].includes(activeTab)" class="manager-footer">
            <a-space>
              <a-button type="primary" :disabled="!detail.editable || changedCount === 0 || busy" :loading="busy" @click="preview"><template #icon><icon-eye /></template>{{ t('characterAdmin.preview') }} ({{ changedCount }})</a-button>
              <a-button :disabled="busy || changedCount === 0" @click="reset"><template #icon><icon-undo /></template>{{ t('characterAdmin.reset') }}</a-button>
              <a-button :disabled="busy" @click="loadHistory"><template #icon><icon-history /></template>{{ t('characterAdmin.history') }}</a-button>
            </a-space>
          </div>
        </template>
      </a-spin>
    </section>
    <a-modal v-model:visible="previewVisible" :title="t('characterAdmin.confirmTitle')" width="min(800px,94vw)" :footer="false" :mask-closable="false" :closable="!busy" :esc-to-close="!busy">
      <a-alert type="warning">{{ t('characterAdmin.changeWarning') }}</a-alert>
      <p>{{ detail?.values.name }} · #{{ cid }}</p>
      <a-table :data="diff" :pagination="false" :scroll="{x:500}" row-key="key">
        <template #columns>
          <a-table-column :title="t('characterAdmin.attribute')"><template #cell="{record}">{{ t('characterAdmin.field.' + record.key) }}</template></a-table-column>
          <a-table-column :title="t('characterAdmin.before')" data-index="before" />
          <a-table-column :title="t('characterAdmin.after')" data-index="after" />
        </template>
      </a-table>
      <div class="modal-footer"><a-button :disabled="busy" @click="previewVisible = false">{{ t('characterAdmin.cancel') }}</a-button><a-button type="primary" :loading="busy" @click="confirm"><template #icon><icon-check /></template>{{ t('characterAdmin.confirm') }}</a-button></div>
    </a-modal>
    <a-modal v-model:visible="historyVisible" :title="t('characterAdmin.history')" width="min(850px,94vw)" :footer="false">
      <a-table :data="history" :pagination="{pageSize:10}" :scroll="{x:650}" row-key="id">
        <template #columns>
          <a-table-column :title="t('characterAdmin.time')" data-index="createdAt" :width="170" />
          <a-table-column :title="t('characterAdmin.actor')" data-index="actor" :width="100" />
          <a-table-column :title="t('characterAdmin.changes')"><template #cell="{record}"><div v-for="change in record.changes" :key="change.key">{{ t('characterAdmin.field.' + change.key) }}: {{ change.before }} → {{ change.after }}</div></template></a-table-column>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { Message } from '@arco-design/web-vue';
  import CharacterPicker from '@/components/character-picker/index.vue';
  import ResourcePanel from './resource-panel.vue';
  import InventoryPanel from './inventory-panel.vue';
  import SkillsPanel from './skills-panel.vue';
  import { characterDetail, characterHistory, previewCharacter, saveCharacter, CharacterDetail, CharacterEdit, CharacterDiff } from '@/api/character-admin';

  const { t } = useI18n();
  const cid = ref<number>();
  const detail = ref<CharacterDetail>();
  const form = ref<Record<string, any>>({});
  const activeTab = ref('basic');
  const groups = ['basic','appearance','location','inventory','progress','account'];
  const readOnlyKeys = ['id','accountid','world','name','guildid','guildrank','party','familyId','partnerId','createdate','lastLogoutTime'];
  const loading = ref(false);
  const operationBusy = ref(false);
  const resourceBusy = ref(false);
  const skillBusy = ref(false);
  const busy = computed(() => operationBusy.value || resourceBusy.value || skillBusy.value);
  const previewVisible = ref(false);
  const historyVisible = ref(false);
  const diff = ref<CharacterDiff[]>([]);
  const history = ref<any[]>([]);
  let request: CharacterEdit | undefined;
  let generation = 0;
  const changes = computed(() => {
    const result: Record<string, any> = {};
    detail.value?.fields.forEach(field => {
      if (form.value[field.key] !== detail.value?.values[field.key]) result[field.key] = form.value[field.key];
    });
    return result;
  });
  const changedCount = computed(() => Object.keys(changes.value).length);
  function reset() { form.value = { ...detail.value?.values }; request = undefined; previewVisible.value = false; }
  async function loadDetail() {
    const token = ++generation;
    previewVisible.value = false; historyVisible.value = false; request = undefined; detail.value = undefined;
    if (!cid.value) { loading.value = false; return; }
    loading.value = true;
    try { const { data } = await characterDetail(cid.value); if (token === generation) { detail.value = data; reset(); } }
    finally { if (token === generation) loading.value = false; }
  }
  function spValue(index: number) { return Number(String(form.value.sp || '').split(',')[index] || 0); }
  function setSp(index: number, value: any) { const values = Array.from({length:10},(_,i) => spValue(i)); values[index] = value; form.value.sp = values.join(','); }
  function display(value: any) { return value === null || value === undefined ? '-' : String(value); }
  async function preview() {
    if (!cid.value || !detail.value?.editable) return;
    operationBusy.value = true;
    try {
      const pending = { cid: cid.value, revision: detail.value.revision, changes: { ...changes.value } };
      const { data } = await previewCharacter(pending);
      request = { ...pending, previewHash: data.previewHash }; diff.value = data.diff; previewVisible.value = true;
    } finally { operationBusy.value = false; }
  }
  async function confirm() {
    if (!request || busy.value) return;
    operationBusy.value = true;
    try {
      await saveCharacter(request); previewVisible.value = false; request = undefined;
      Message.success(t('message.success')); await loadDetail();
    } catch (error) { previewVisible.value = false; request = undefined; await loadDetail(); }
    finally { operationBusy.value = false; }
  }
  async function loadHistory() {
    if (!cid.value) return;
    operationBusy.value = true;
    try { const { data } = await characterHistory(cid.value); history.value = data; historyVisible.value = true; }
    finally { operationBusy.value = false; }
  }
  onBeforeUnmount(() => { generation += 1; });
</script>

<style scoped lang="less">
  .character-manager { padding: 0 20px 20px; }
  .manager-body { background: var(--color-bg-2); padding: 20px; }
  .manager-header { display:flex; align-items:center; gap:12px; flex-wrap:wrap; margin-bottom:20px; }
  h2 { margin:0; font-size:20px; margin-right:auto; }
  .picker { width:440px; max-width:calc(100% - 44px); min-width:0; }
  .identity { margin-bottom:16px; }
  .editor-tabs { margin-top:20px; }
  .attribute-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:0 24px; padding:16px 0; }
  .sp-grid { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:6px; width:100%; }
  .sp-field { grid-column:1 / -1; }
  .group-alert { margin-top:12px; }
  .manager-footer { border-top:1px solid var(--color-border-2); padding-top:16px; }
  .modal-footer { display:flex; justify-content:flex-end; gap:12px; margin-top:20px; }
  @media (max-width:900px) { .attribute-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
  @media (max-width:600px) { .character-manager { padding:0 8px 12px; } .manager-body { padding:12px; } .attribute-grid { grid-template-columns:minmax(0,1fr); } .sp-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } .picker { width:100%; } .manager-footer :deep(.arco-space) { flex-wrap:wrap; } }
</style>
