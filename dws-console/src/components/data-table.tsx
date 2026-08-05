import {
	flexRender,
	type RowData,
	type Table as TanTable,
} from "@tanstack/react-table";

// Column-meta augmentation: per-column layout hints shared by the head/body
// renderers below, so the Organic table markup (widths + mono/muted cell
// classes) survives the move to TanStack Table.
declare module "@tanstack/react-table" {
	interface ColumnMeta<TData extends RowData, TValue> {
		width?: string;
		cellClass?: string;
	}
}

/** `<thead>` driven by a TanStack table instance, applying per-column widths. */
export function DataTableHead<T>({ table }: { table: TanTable<T> }) {
	return (
		<thead>
			{table.getHeaderGroups().map((hg) => (
				<tr key={hg.id}>
					{hg.headers.map((h) => (
						<th key={h.id} style={{ width: h.column.columnDef.meta?.width }}>
							{h.isPlaceholder
								? null
								: flexRender(h.column.columnDef.header, h.getContext())}
						</th>
					))}
				</tr>
			))}
		</thead>
	);
}

/** `<tbody>` of flat rows; whole-row click optional. */
export function DataTableRows<T>({
	table,
	onRowClick,
}: {
	table: TanTable<T>;
	onRowClick?: (row: T) => void;
}) {
	return (
		<tbody>
			{table.getRowModel().rows.map((row) => (
				<tr
					key={row.id}
					className={onRowClick ? "clickable" : undefined}
					onClick={onRowClick ? () => onRowClick(row.original) : undefined}
				>
					{row.getVisibleCells().map((cell) => (
						<td key={cell.id} className={cell.column.columnDef.meta?.cellClass}>
							{flexRender(cell.column.columnDef.cell, cell.getContext())}
						</td>
					))}
				</tr>
			))}
		</tbody>
	);
}
