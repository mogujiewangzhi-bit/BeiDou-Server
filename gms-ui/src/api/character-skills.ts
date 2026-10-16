import axios from 'axios';

export interface SkillOption {
  id: number; name: string; job: number; jobName: string; maxLevel: number;
  masterRequired: boolean; compatible: boolean; gmSkill: boolean; known: boolean;
  level?: number; masterLevel?: number; expiration?: number;
  bindable?: boolean;
}
export interface SkillKeyOption {key:number;label:string;binding:{key:number;type:number;action:number}|null;}
export interface SkillEdit {
  cid: number; revision: string; skillId: number; action: 'set' | 'remove';
  level?: number; masterLevel?: number; allowCross: boolean; previewHash?: string;
  keyMode?: 'auto'|'key'|'none'; keyCode?: number; replaceKey?: boolean;
}
export interface SkillDiff { skillId: number; action: string; cross: boolean; before: any; after: any; keybinding?:{status:string;key?:number;label?:string;before:any[];after:any[]}; }
export const skillCatalog = (cid: number, q = '', page = 1) => axios.get<{records:SkillOption[];hasMore:boolean}>('/character/v1/skills/catalog', {params:{cid,q,page},timeout:15000});
export const characterSkills = (cid: number) => axios.get<{records:SkillOption[];revision:string;editable:boolean;keyOptions:SkillKeyOption[]}>(`/character/v1/skills/${cid}`);
export const previewSkill = (edit: SkillEdit) => axios.post<{diff:SkillDiff;previewHash:string;name:string}>('/character/v1/skills/preview', edit);
export const saveSkill = (edit: SkillEdit) => axios.post('/character/v1/skills/save', edit);
export const skillHistory = (cid: number) => axios.get<any[]>(`/character/v1/skills/history/${cid}`);
