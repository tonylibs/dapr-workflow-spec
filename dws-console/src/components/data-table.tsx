import {
	type CellData,
	flexRender,
	type RowData,
	type TableFeatures,
	type Table as TanTable,
} from "@tanstack/react-table";

// Column-meta augmentation: per-column layout hints shared by the head/body
// renderers below, so the Organic table markup (widths + mono/muted cell
// classes) survives the move to TanStack Table. The type-parameter list must
// match table-core's `ColumnMeta` declaration exactly (v9 added `TFeatures`).
declare module "@tanstack/react-table" {
	interface ColumnMeta<
		in out TFeatures extends TableFeatures,
		in out TData extends RowData,
		TValue extends CellData = CellData,
	> {
		width?: string;
		cellClass?: string;
	}
}

// v9 types `columnDef.meta` as a union that includes a bare `object` slot, so
// reads go through this narrow view rather than the augmented interface.
type CellMeta = { width?: string; cellClass?: string };

/** `<thead>` driven by a TanStack table instance, applying per-column widths. */
export function DataTableHead<TF extends TableFeatures, T extends RowData>({
	table,
}: {
	table: TanTable<TF, T>;
}) {
	return (
		<thead>
			{table.getHeaderGroups().map((hg) => (
				<tr key={hg.id}>
					{hg.headers.map((h) => (
						<th
							key={h.id}
							style={{ width: (h.column.columnDef.meta as CellMeta)?.width }}
						>
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
export function DataTableRows<TF extends TableFeatures, T extends RowData>({
	table,
	onRowClick,
}: {
	table: TanTable<TF, T>;
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
					{row.getAllCells().map((cell) => (
						<td
							key={cell.id}
							className={(cell.column.columnDef.meta as CellMeta)?.cellClass}
						>
							{flexRender(cell.column.columnDef.cell, cell.getContext())}
						</td>
					))}
				</tr>
			))}
		</tbody>
	);
}
