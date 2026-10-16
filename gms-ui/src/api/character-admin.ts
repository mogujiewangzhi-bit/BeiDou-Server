import axios from 'axios';

export interface CharacterOption {
  id: number; name: string; account: string; world: number;
  job: number; jobName: string; level: number; online: boolean;
}
export interface CharacterField { key: string; group: string; type: string; min: number; max: number; }
export interface CharacterDetail {
  values: Record<string, any>; revision: string; editable: boolean; online: boolean;
  fields: CharacterField[]; jobs: { value: number; label: string }[];
}
export interface CharacterEdit { cid: number; revision: string; changes: Record<string, any>; previewHash?: string; }
export interface CharacterDiff { key: string; before: any; after: any; }
export function characterOptions(q = '', world?: number, id?: number, page = 1) {
  return axios.get<{ records: CharacterOption[]; hasMore: boolean }>('/character/v1/options', { params: { q, world, id, page } });
}
export function characterDetail(cid: number) { return axios.get<CharacterDetail>(`/character/v1/manage/${cid}`); }
export function previewCharacter(edit: CharacterEdit) {
  return axios.post<{ diff: CharacterDiff[]; previewHash: string; characterName: string }>('/character/v1/manage/preview', edit);
}
export function saveCharacter(edit: CharacterEdit) { return axios.post<{ auditId: string }>('/character/v1/manage/save', edit); }
export function characterHistory(cid: number) {
  return axios.get<{ id: string; actor: string; createdAt: string; changes: CharacterDiff[] }[]>(`/character/v1/manage/history/${cid}`);
}
