<template>
  <section class="skills-panel">
    <a-form :model="form" layout="vertical" :disabled="busy || !editable" class="skill-grid">
      <a-form-item :label="t('skillAdmin.skill')" class="skill-picker">
        <a-select v-model="selectedId" allow-search allow-clear :filter-option="false" :search-delay="0" :loading="searching" :placeholder="t('skillAdmin.search')" @search="search" @change="select">
          <a-option v-for="skill in choices" :key="skill.id" :value="skill.id" :label="`${skill.name} · #${skill.id} · ${skill.jobName}`" />
          <template v-if="hasMore" #footer><a-button type="text" :loading="searching" @click="loadMore">{{ t('characterAdmin.more') }}</a-button></template>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('skillAdmin.level')"><a-input-number v-model="form.level" :min="1" :max="selected?.maxLevel || 1" :precision="0" /></a-form-item>
      <a-form-item :label="t('skillAdmin.master')"><a-input-number v-model="form.masterLevel" :disabled="!selected?.masterRequired" :min="0" :max="selected?.maxLevel || 0" :precision="0" /></a-form-item>
      <a-form-item v-if="selected && bindable" :label="t('skillAdmin.shortcut')" class="shortcut-picker">
        <a-select v-model="form.keyMode" @change="changeKeyMode">
          <a-option value="auto">{{ t('skillAdmin.keyAuto') }}</a-option>
          <a-option value="key">{{ t('skillAdmin.keyChoose') }}</a-option>
          <a-option value="none">{{ t('skillAdmin.keyNone') }}</a-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="selected && bindable && form.keyMode === 'key'" :label="t('skillAdmin.key')">
        <a-select v-model="form.keyCode" allow-search>
          <a-option v-for="option in keyOptions" :key="option.key" :value="option.key" :label="keyLabel(option)" />
        </a-select>
      </a-form-item>
      <a-form-item v-if="selected && bindable && form.keyMode === 'key' && occupied" :label="t('skillAdmin.keyConflict')">
        <a-checkbox v-model="form.replaceKey">{{ t('skillAdmin.keyReplace') }}</a-checkbox>
      </a-form-item>
    </a-form>
    <a-tag v-if="selected && !bindable" color="gray">{{ t('skillAdmin.notBindable') }}</a-tag>
    <a-alert v-if="selected && !selected.compatible" type="warning" class="cross-alert">{{ t('skillAdmin.crossWarning') }}</a-alert>
    <a-checkbox v-if="selected && !selected.compatible" v-model="form.allowCross" :disabled="busy || !editable">{{ t('skillAdmin.allowCross') }}</a-checkbox>
    <a-space wrap class="skill-actions">
      <a-button :disabled="!selected || !editable || busy" @click="max"><template #icon><icon-plus /></template>{{ t('skillAdmin.max') }}</a-button>
      <a-button type="primary" :disabled="!valid || busy" :loading="busy" @click="previewSet"><template #icon><icon-eye /></template>{{ t('characterAdmin.preview') }}</a-button>
      <a-button :disabled="busy" :loading="loading" @click="reload"><template #icon><icon-refresh /></template>{{ t('button.refresh') }}</a-button>
      <a-button :disabled="busy" @click="historyClick"><template #icon><icon-history /></template>{{ t('skillAdmin.history') }}</a-button>
    </a-space>
    <a-table :data="records" :loading="loading" :pagination="{pageSize:15}" :scroll="{x:850}" row-key="id">
      <template #columns>
        <a-table-column title="ID" data-index="id" :width="110" />
        <a-table-column :title="t('skillAdmin.skill')" data-index="name" :width="200" />
        <a-table-column :title="t('skillAdmin.job')" data-index="jobName" :width="100" />
        <a-table-column :title="t('skillAdmin.level')" data-index="level" :width="80" />
        <a-table-column :title="t('skillAdmin.master')" data-index="masterLevel" :width="100" />
        <a-table-column :title="t('skillAdmin.shortcut')" :width="120"><template #cell="{record}">{{ boundKeys(record.id) || '-' }}</template></a-table-column>
        <a-table-column :title="t('skillAdmin.expiration')" :width="130"><template #cell="{record}">{{ record.expiration < 0 ? t('skillAdmin.permanent') : new Date(record.expiration).toLocaleString() }}</template></a-table-column>
        <a-table-column :title="t('skillAdmin.operation')" :width="140"><template #cell="{record}">
          <a-button type="text" :disabled="!editable || busy || !record.known" @click="editRow(record)">{{ t('skillAdmin.edit') }}</a-button>
          <a-button type="text" status="danger" :disabled="!editable || busy" @click="previewRemove(record)">{{ t('skillAdmin.remove') }}</a-button>
        </template></a-table-column>
      </template>
    </a-table>
    <a-modal v-model:visible="visible" :title="t('skillAdmin.confirm')" width="min(650px,94vw)" :footer="false" :mask-closable="false" :closable="!busy" :esc-to-close="!busy">
      <p>{{ name }} · #{{ cid }} · {{ selectedName }} · #{{ diff?.skillId }}</p>
      <a-alert v-if="diff?.cross" type="warning">{{ t('skillAdmin.crossWarning') }}</a-alert>
      <a-descriptions :column="1" bordered>
        <a-descriptions-item :label="t('characterAdmin.before')">{{ stateLabel(diff?.before) }}</a-descriptions-item>
        <a-descriptions-item :label="t('characterAdmin.after')">{{ stateLabel(diff?.after) }}</a-descriptions-item>
        <a-descriptions-item v-if="diff?.keybinding" :label="t('skillAdmin.shortcut')">{{ shortcutLabel(diff.keybinding) }}</a-descriptions-item>
        <a-descriptions-item v-if="diff?.keybinding?.before.length" :label="t('skillAdmin.keyPrevious')">{{ diff.keybinding.before.map(binding => bindingLabel(binding)).join(', ') }}</a-descriptions-item>
      </a-descriptions>
      <a-space class="confirm-actions"><a-button :disabled="busy" @click="visible=false">{{ t('characterAdmin.cancel') }}</a-button><a-button type="primary" :loading="busy" @click="confirm">{{ t('characterAdmin.confirm') }}</a-button></a-space>
    </a-modal>
    <a-modal v-model:visible="historyVisible" :title="t('skillAdmin.history')" width="min(800px,94vw)" :footer="false">
      <a-table :data="history" :pagination="{pageSize:10}" :scroll="{x:650}" row-key="id">
        <template #columns>
          <a-table-column :title="t('characterAdmin.time')" data-index="createdAt" :width="170" />
          <a-table-column :title="t('characterAdmin.actor')" data-index="actor" :width="100" />
          <a-table-column :title="t('skillAdmin.skill')"><template #cell="{record}">#{{ record.changes.skillId }}: {{ stateLabel(record.changes.before) }} → {{ stateLabel(record.changes.after) }}<div v-if="record.changes.keybinding">{{ shortcutLabel(record.changes.keybinding) }}</div></template></a-table-column>
        </template>
      </a-table>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { useI18n } from 'vue-i18n';
  import { characterSkills, previewSkill, saveSkill, skillCatalog, skillHistory, SkillOption, SkillEdit, SkillDiff, SkillKeyOption } from '@/api/character-skills';
  const props=defineProps<{cid:number;name:string;editable:boolean}>();
  const emit=defineEmits<{(event:'busy',value:boolean):void}>();
  const {t}=useI18n();
  const records=ref<SkillOption[]>([]), options=ref<SkillOption[]>([]);
  const selectedId=ref<number>(), selected=ref<SkillOption>();
  const selectedName=ref('');
  const revision=ref(''), editable=ref(false), busy=ref(false), loading=ref(false), searching=ref(false);
  const form=ref<{level?:number;masterLevel:number;allowCross:boolean;keyMode:'auto'|'key'|'none';keyCode?:number;replaceKey:boolean}>({masterLevel:0,allowCross:false,keyMode:'auto',replaceKey:false});
  const keyOptions=ref<SkillKeyOption[]>([]);
  const bindable=computed(()=>!!selected.value && (selected.value.bindable ?? selected.value.id%10000>=1000));
  const occupied=computed(()=>{const binding=keyOptions.value.find(option=>option.key===form.value.keyCode)?.binding;return !!binding && !(binding.type===1 && binding.action===selected.value?.id);});
  const visible=ref(false), historyVisible=ref(false), history=ref<any[]>([]), diff=ref<SkillDiff>();
  const hasMore=ref(false);let page=1, query='', generation=0, searchGeneration=0, timer:ReturnType<typeof setTimeout>|undefined, pending:SkillEdit|undefined;
  const choices=computed(()=>selected.value&&!options.value.some(skill=>skill.id===selected.value?.id)?[selected.value,...options.value]:options.value);
  const valid=computed(()=>editable.value&&props.editable&&selected.value?.known&&form.value.level!==undefined&&form.value.level>=1&&form.value.level<=selected.value.maxLevel&&(!selected.value.masterRequired||form.value.masterLevel>=(form.value.level||0))&&(selected.value.compatible||form.value.allowCross)&&(!bindable.value||form.value.keyMode!=='key'||(form.value.keyCode!==undefined&&(!occupied.value||form.value.replaceKey))));
  watch(busy,value=>emit('busy',value));
  function stateLabel(state:any){return state?`${t('skillAdmin.level')} ${state.level} / ${t('skillAdmin.master')} ${state.masterLevel}`:t('skillAdmin.absent');}
  function boundKeys(id:number){return keyOptions.value.filter(option=>option.binding?.type===1&&option.binding.action===id).map(option=>option.label).join(', ');}
  function keyLabel(option:SkillKeyOption){return option.label+(option.binding?` · ${bindingLabel(option.binding)}`:'');}
  function bindingLabel(binding:any){return binding.type===1?(records.value.find(skill=>skill.id===binding.action)?.name || `${t('skillAdmin.skill')} #${binding.action}`):`${t('skillAdmin.keyOccupied')} #${binding.action}`;}
  function shortcutLabel(binding:NonNullable<SkillDiff['keybinding']>){return t('skillAdmin.keyStatus.'+binding.status)+(binding.label?` · ${binding.label}`:'');}
  function changeKeyMode(){form.value.keyCode=undefined;form.value.replaceKey=false;}
  async function reload(){const token=++generation;loading.value=true;visible.value=false;pending=undefined;try{const {data}=await characterSkills(props.cid);if(token===generation){records.value=data.records;keyOptions.value=data.keyOptions||[];revision.value=data.revision;editable.value=data.editable&&props.editable;}}finally{if(token===generation)loading.value=false;}}
  async function loadOptions(append=false){const token=++searchGeneration;searching.value=true;const current=page;try{const {data}=await skillCatalog(props.cid,query,current);if(token===searchGeneration){options.value=append?[...options.value,...data.records]:data.records;hasMore.value=data.hasMore;}}catch(error){if(token===searchGeneration&&append)page-=1;}finally{if(token===searchGeneration)searching.value=false;}}
  function search(value:string){clearTimeout(timer);searchGeneration++;query=value;page=1;options.value=[];hasMore.value=false;timer=setTimeout(()=>loadOptions(),250);}
  function loadMore(){if(searching.value||!hasMore.value)return;page++;loadOptions(true);}
  function select(id:any){selected.value=choices.value.find(skill=>skill.id===id);form.value={level:selected.value?1:undefined,masterLevel:selected.value?.masterRequired?selected.value.maxLevel:0,allowCross:false,keyMode:'auto',replaceKey:false};pending=undefined;}
  function editRow(skill:SkillOption){selected.value=skill;selectedId.value=skill.id;form.value={level:skill.level,masterLevel:skill.masterRequired?skill.masterLevel||0:0,allowCross:false,keyMode:'auto',replaceKey:false};}
  function max(){if(selected.value){form.value.level=selected.value.maxLevel;form.value.masterLevel=selected.value.masterRequired?selected.value.maxLevel:0;}}
  async function preview(request:SkillEdit,label:string){if(busy.value)return;busy.value=true;try{const {data}=await previewSkill(request);pending={...request,previewHash:data.previewHash};diff.value=data.diff;selectedName.value=label;visible.value=true;}finally{busy.value=false;}}
  function previewSet(){if(!valid.value||!selected.value)return;return preview({cid:props.cid,revision:revision.value,skillId:selected.value.id,action:'set',...form.value,keyMode:bindable.value?form.value.keyMode:'auto',keyCode:bindable.value&&form.value.keyMode==='key'?form.value.keyCode:undefined,replaceKey:bindable.value&&form.value.keyMode==='key'?form.value.replaceKey:false},selected.value.name);}
  function previewRemove(skill:SkillOption){return preview({cid:props.cid,revision:revision.value,skillId:skill.id,action:'remove',allowCross:false},skill.name);}
  async function confirm(){if(!pending||busy.value)return;busy.value=true;try{await saveSkill(pending);Message.success(t('message.success'));await reload();}catch(error){await reload();}finally{busy.value=false;}}
  async function historyClick(){busy.value=true;try{const {data}=await skillHistory(props.cid);history.value=data;historyVisible.value=true;}finally{busy.value=false;}}
  watch(()=>props.editable,value=>{if(!value)editable.value=false;});
  reload();loadOptions();
  onBeforeUnmount(()=>{generation++;searchGeneration++;clearTimeout(timer);emit('busy',false);});
</script>

<style scoped lang="less">
  .skills-panel { padding:16px 0; min-width:0; }
  .skill-grid { display:grid; grid-template-columns:minmax(0,2fr) repeat(2,minmax(0,1fr)); gap:0 20px; }
  .shortcut-picker { grid-column:1; }
  .skill-actions { margin:16px 0; display:flex; }
  .cross-alert { margin-bottom:12px; }
  .confirm-actions { margin-top:20px; display:flex; justify-content:flex-end; }
  @media(max-width:700px){.skill-grid{grid-template-columns:repeat(2,minmax(0,1fr));}.skill-picker{grid-column:1/-1;}}
</style>
