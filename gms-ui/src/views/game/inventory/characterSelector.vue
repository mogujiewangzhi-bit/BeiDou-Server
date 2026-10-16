<template>
  <CharacterPicker v-model="cid" style="width:420px;max-width:100%" @select="select" />
</template>
<script setup lang="ts">
  import { ref } from 'vue';
  import CharacterPicker from '@/components/character-picker/index.vue';
  import { CharacterOption } from '@/api/character-admin';
  const cid = ref<number>();
  const emit = defineEmits(['useCharacter']);
  function select(character?: CharacterOption) {
    emit('useCharacter', character?.id, character?.name, character?.online || false);
  }
</script>
