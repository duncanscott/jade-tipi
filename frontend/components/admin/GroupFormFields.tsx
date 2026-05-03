'use client';

export type AccessLevel = 'rw' | 'r';

export interface PermissionEntry {
  grpId: string;
  access: AccessLevel;
}

export interface GroupFormState {
  id: string;
  name: string;
  description: string;
  permissionEntries: PermissionEntry[];
}

interface GroupFormFieldsProps {
  form: GroupFormState;
  onChange: (next: GroupFormState) => void;
  mode: 'create' | 'edit';
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '0.5rem',
  border: '1px solid var(--border, #d1d5db)',
  borderRadius: '0.375rem',
  fontSize: '0.875rem',
};

export default function GroupFormFields({ form, onChange, mode }: GroupFormFieldsProps) {
  function update(patch: Partial<GroupFormState>) {
    onChange({ ...form, ...patch });
  }

  function updateEntry(index: number, patch: Partial<PermissionEntry>) {
    const next = form.permissionEntries.map((entry, i) =>
      i === index ? { ...entry, ...patch } : entry
    );
    onChange({ ...form, permissionEntries: next });
  }

  function addEntry() {
    onChange({
      ...form,
      permissionEntries: [...form.permissionEntries, { grpId: '', access: 'rw' }],
    });
  }

  function removeEntry(index: number) {
    onChange({
      ...form,
      permissionEntries: form.permissionEntries.filter((_, i) => i !== index),
    });
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      {mode === 'create' && (
        <div>
          <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem' }}>
            Group ID (optional)
          </label>
          <input
            type="text"
            value={form.id}
            onChange={(e) => update({ id: e.target.value })}
            placeholder="leave blank to auto-generate"
            style={inputStyle}
            data-testid="admin-group-field-id"
          />
        </div>
      )}

      <div>
        <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem' }}>
          Name <span style={{ color: '#ef4444' }}>*</span>
        </label>
        <input
          type="text"
          required
          value={form.name}
          onChange={(e) => update({ name: e.target.value })}
          style={inputStyle}
          data-testid="admin-group-field-name"
        />
      </div>

      <div>
        <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem' }}>
          Description
        </label>
        <textarea
          value={form.description}
          onChange={(e) => update({ description: e.target.value })}
          rows={3}
          style={inputStyle}
          data-testid="admin-group-field-description"
        />
      </div>

      <fieldset
        style={{
          border: '1px solid var(--border, #e5e7eb)',
          borderRadius: '0.375rem',
          padding: '0.75rem',
        }}
        data-testid="admin-group-permissions"
      >
        <legend style={{ fontWeight: 500, padding: '0 0.25rem' }}>Permissions</legend>
        {form.permissionEntries.length === 0 && (
          <p style={{ margin: 0, color: 'var(--muted, #6b7280)', fontSize: '0.875rem' }}>
            No permission entries yet.
          </p>
        )}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.5rem' }}>
          {form.permissionEntries.map((entry, index) => (
            <div
              key={index}
              style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}
              data-testid="admin-group-permission-row"
            >
              <input
                type="text"
                value={entry.grpId}
                onChange={(e) => updateEntry(index, { grpId: e.target.value })}
                placeholder="grp id"
                style={{ ...inputStyle, flex: 1 }}
                data-testid="admin-group-permission-grpid"
              />
              <select
                value={entry.access}
                onChange={(e) => updateEntry(index, { access: e.target.value as AccessLevel })}
                style={{ ...inputStyle, width: 'auto' }}
                data-testid="admin-group-permission-access"
              >
                <option value="rw">rw</option>
                <option value="r">r</option>
              </select>
              <button
                type="button"
                onClick={() => removeEntry(index)}
                style={{
                  padding: '0.5rem 0.75rem',
                  background: 'transparent',
                  border: '1px solid var(--border, #d1d5db)',
                  borderRadius: '0.375rem',
                  cursor: 'pointer',
                }}
                data-testid="admin-group-permission-remove"
              >
                Remove
              </button>
            </div>
          ))}
        </div>
        <button
          type="button"
          onClick={addEntry}
          style={{
            marginTop: '0.5rem',
            padding: '0.5rem 0.75rem',
            background: 'transparent',
            border: '1px dashed var(--border, #d1d5db)',
            borderRadius: '0.375rem',
            cursor: 'pointer',
            fontSize: '0.875rem',
          }}
          data-testid="admin-group-permission-add"
        >
          Add permission entry
        </button>
      </fieldset>
    </div>
  );
}
