const fields = {
  level:'Level',job:'Job',exp:'Current level EXP',str:'STR',dex:'DEX',int:'INT',luk:'LUK',hp:'Current HP',maxhp:'Base maximum HP',mp:'Current MP',maxmp:'Base maximum MP',ap:'Remaining AP',sp:'Remaining SP (books 1-10)',hpMpUsed:'AP used for HP/MP',
  meso:'Mesos',fame:'Fame',gm:'GM level',gender:'Gender',skincolor:'Skin ID',face:'Face ID',hair:'Hair ID',map:'Map ID',spawnpoint:'Spawn portal ID',equipslots:'Equipment slots',useslots:'Use slots',setupslots:'Setup slots',etcslots:'Etc slots',buddyCapacity:'Buddy capacity',
  mountlevel:'Mount level',mountexp:'Mount EXP',mounttiredness:'Mount tiredness',reborns:'Rebirth count',pqpoints:'Party quest points',dojoPoints:'Dojo points',ariantPoints:'Ariant points',vanquisherStage:'Vanquisher stage',vanquisherKills:'Vanquisher kills',gachaexp:'Gachapon EXP',nxCredit:'NX Credit',maplePoint:'Maple Points',nxPrepaid:'NX Prepaid',
};
const readonly = {id:'Character ID',accountid:'Account ID',world:'World',name:'Name',guildid:'Guild ID',guildrank:'Guild rank',party:'Party ID',familyId:'Family ID',partnerId:'Partner ID',createdate:'Created at',lastLogoutTime:'Last logout'};
export default {
  ...Object.fromEntries(Object.entries(fields).map(([key,value])=>[`characterAdmin.field.${key}`,value])),
  ...Object.fromEntries(Object.entries(readonly).map(([key,value])=>[`characterAdmin.readonly.${key}`,value])),
  'menu.game.character':'Character Management','characterAdmin.picker':'Select character / search name or ID','characterAdmin.more':'Load more',
  'characterAdmin.online':'Online','characterAdmin.offline':'Offline','characterAdmin.name':'Character','characterAdmin.account':'Account','characterAdmin.world':'World','characterAdmin.status':'Status',
  'characterAdmin.locked':'The character or its account is in game. Log out completely and refresh before saving.',
  'characterAdmin.accountShared':'NX Credit, Maple Points and NX Prepaid are shared by all characters on this account.',
  'characterAdmin.group.basic':'Attributes','characterAdmin.group.appearance':'Appearance','characterAdmin.group.location':'Location','characterAdmin.group.inventory':'Inventory and buddies','characterAdmin.group.progress':'Progress','characterAdmin.group.account':'Account resources',
  'characterAdmin.relations':'Relations (read-only)','characterAdmin.male':'Male','characterAdmin.female':'Female','characterAdmin.preview':'Preview changes','characterAdmin.reset':'Reset inputs','characterAdmin.history':'Edit history','characterAdmin.confirmTitle':'Confirm character changes',
  'characterAdmin.changeWarning':'Changes are saved directly. Job changes do not reset skills; level changes do not award AP/SP. Before and after values are recorded.',
  'characterAdmin.attribute':'Attribute','characterAdmin.before':'Before','characterAdmin.after':'After','characterAdmin.cancel':'Cancel','characterAdmin.confirm':'Confirm changes','characterAdmin.time':'Time','characterAdmin.actor':'Administrator','characterAdmin.changes':'Changes',
};
